package com.github.mysite.common.payonline.alipay.util.httpclient;

import com.google.common.io.CharStreams;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.FilePartSource;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.util.IdleConnectionTimeoutThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * description:HttpClient方式访问，获取远程的Http数据
 *
 * @author : jy.chen
 * @version : 1.0
 * @since : 2015-11-30 11:00
 */
public class AliPayHttpProtocolHandler {
    /**
     * Logger for HttpProtocolHandler
     */
    private static final Logger LOG = LoggerFactory.getLogger(AliPayHttpProtocolHandler.class);

    private static String DEFAULT_CHARSET = "GBK";

    /**
     * 连接超时时间，由bean factory设置，缺省为8秒钟
     */
    private int defaultConnectionTimeout = 8000;

    /**
     * 回应超时时间, 由bean factory设置，缺省为30秒钟
     */
    private int defaultSoTimeout = 30000;

    /**
     * 闲置连接超时时间, 由bean factory设置，缺省为60秒钟
     */
    private int defaultIdleConnTimeout = 60000;

    private int defaultMaxConnPerHost = 30;

    private int defaultMaxTotalConn = 80;

    /**
     * 默认等待HttpConnectionManager返回连接超时（只有在达到最大连接数时起作用）：1秒
     */
    private static final long defaultHttpConnectionManagerTimeout = 3 * 1000;

    /**
     * HTTP连接管理器，该连接管理器必须是线程安全的.
     */
    private HttpConnectionManager connectionManager;

    private static AliPayHttpProtocolHandler aliPayHttpProtocolHandler = new AliPayHttpProtocolHandler();

    /**
     * 工厂方法
     *
     * @return HttpProtocolHandler
     */
    public static AliPayHttpProtocolHandler getInstance() {
        return aliPayHttpProtocolHandler;
    }

    /**
     * 私有的构造方法
     */
    private AliPayHttpProtocolHandler() {
        // 创建一个线程安全的HTTP连接池
        connectionManager = new MultiThreadedHttpConnectionManager();
        connectionManager.getParams().setDefaultMaxConnectionsPerHost(defaultMaxConnPerHost);
        connectionManager.getParams().setMaxTotalConnections(defaultMaxTotalConn);

        IdleConnectionTimeoutThread ict = new IdleConnectionTimeoutThread();
        ict.addConnectionManager(connectionManager);
        ict.setConnectionTimeout(defaultIdleConnTimeout);

        ict.start();
    }

    /**
     * 执行Http请求
     *
     * @param request         请求数据
     * @param strParaFileName 文件类型的参数名
     * @param strFilePath     文件路径
     * @return AlipayHttpResponse
     * @throws IOException
     */
    public AliPayHttpResponse execute(AliPayHttpRequest request, String strParaFileName, String strFilePath) throws IOException {
        HttpClient httpclient = new HttpClient(connectionManager);

        // 设置连接超时
        int connectionTimeout = defaultConnectionTimeout;
        if (request.getConnectionTimeout() > 0) {
            connectionTimeout = request.getConnectionTimeout();
        }
        httpclient.getHttpConnectionManager().getParams().setConnectionTimeout(connectionTimeout);

        // 设置回应超时
        int soTimeout = defaultSoTimeout;
        if (request.getTimeout() > 0) {
            soTimeout = request.getTimeout();
        }
        httpclient.getHttpConnectionManager().getParams().setSoTimeout(soTimeout);

        // 设置等待ConnectionManager释放connection的时间
        httpclient.getParams().setConnectionManagerTimeout(defaultHttpConnectionManagerTimeout);

        String charset = request.getCharset();
        charset = charset == null ? DEFAULT_CHARSET : charset;
        HttpMethod method;

        //get模式且不带上传文件
        if (request.getMethod().equals(AliPayHttpRequest.METHOD_GET)) {
            method = new GetMethod(request.getUrl());
            method.getParams().setCredentialCharset(charset);

            // parseNotifyConfig会保证使用GET方法时，request一定使用QueryString
            method.setQueryString(request.getQueryString());
        } else if (strParaFileName.equals("") && strFilePath.equals("")) {
            //post模式且不带上传文件
            method = new PostMethod(request.getUrl());
            ((PostMethod) method).addParameters(request.getParameters());
            method.addRequestHeader("Content-Type", "application/x-www-form-urlencoded; text/html; charset=" + charset);
        } else {
            //post模式且带上传文件
            method = new PostMethod(request.getUrl());
            List<Part> parts = new ArrayList<Part>();
            for (int i = 0; i < request.getParameters().length; i++) {
                parts.add(new StringPart(request.getParameters()[i].getName(), request.getParameters()[i].getValue(), charset));
            }
            //增加文件参数，strParaFileName是参数名，使用本地文件
            parts.add(new FilePart(strParaFileName, new FilePartSource(new File(strFilePath))));

            // 设置请求体
            ((PostMethod) method).setRequestEntity(new MultipartRequestEntity(parts.toArray(new Part[0]), new HttpMethodParams()));
        }

        // 设置Http Header中的User-Agent属性
        method.addRequestHeader("User-Agent", "Mozilla/4.0");
        AliPayHttpResponse response = new AliPayHttpResponse();

        try {

            //	设置成了默认的恢复策略，在发生异常时候将自动重试3次，在这里你也可以设置成自定义的恢复策略
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler()); //执行getMethod
            int statusCode = httpclient.executeMethod(method);
            if (statusCode != HttpStatus.SC_OK) {
                LOG.error("Method failed: " + method.getStatusLine());
            }
            // Going to buffer response body of large or unknown size. Using getResponseBodyAsStream instead is recommended.
            BufferedReader reader = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream()));
            //use guava lib
            String stringResult = CharStreams.toString(reader);

           // httpclient.executeMethod(method);
            if (request.getResultType().equals(AliPayHttpResultType.STRING)) {
               response.setStringResult(stringResult);
                //StringBuilder stringBuilder = new StringBuilder();
                //String str;
                //while ((str = reader.readLine()) != null) {
                //    stringBuilder.append(str);
                //}
                //response.setStringResult(stringBuilder.toString());
                //response.setStringResult(method.getResponseBodyAsString());
            } else if (request.getResultType().equals(AliPayHttpResultType.BYTES)) {
                response.setByteResult(stringResult.getBytes());
               // response.setByteResult(method.getResponseBody());
            }
            response.setResponseHeaders(method.getResponseHeaders());
        } catch (Exception ex) {
            LOG.error("HttpClient executeMethod fail , detail msg : [{}]",ex);
            return null;
        } finally {
            method.releaseConnection();
        }
        return response;
    }

    /**
     * 将NameValuePairs数组转变为字符串
     *
     * @param nameValues
     * @return
     */
    protected String toString(NameValuePair[] nameValues) {
        if (nameValues == null || nameValues.length == 0) {
            return "null";
        }

        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < nameValues.length; i++) {
            NameValuePair nameValue = nameValues[i];

            if (i == 0) {
                buffer.append(nameValue.getName()).append("=").append(nameValue.getValue());
            } else {
                buffer.append("&").append(nameValue.getName()).append("=").append(nameValue.getValue());
            }
        }

        return buffer.toString();
    }

}
