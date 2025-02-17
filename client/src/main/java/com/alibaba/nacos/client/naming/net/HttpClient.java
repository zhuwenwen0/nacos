/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.client.naming.net;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.naming.utils.IoUtils;
import com.alibaba.nacos.client.naming.utils.StringUtils;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * @author nkorange
 */
public class HttpClient {

    public static final int TIME_OUT_MILLIS = Integer
        .getInteger("com.alibaba.nacos.client.naming.ctimeout", 50000);
    public static final int CON_TIME_OUT_MILLIS = Integer
        .getInteger("com.alibaba.nacos.client.naming.ctimeout", 3000);
    private static final boolean ENABLE_HTTPS = Boolean
        .getBoolean("com.alibaba.nacos.client.naming.tls.enable");

    private static final String POST = "POST";
    private static final String PUT = "PUT";

    static {
        // limit max redirection
        System.setProperty("http.maxRedirects", "5");
    }

    public static String getPrefix() {
        if (ENABLE_HTTPS) {
            return "https://";
        }

        return "http://";

    }

    public static HttpResult httpGet(String url, List<String> headers, Map<String, String> paramValues, String encoding) {
        return request(url, headers, paramValues, encoding, "GET");
    }

    /**
     * http请求构建和返回参数封装,put和post方法，只是用来新增和修改的，所以不含有返回值，只有get会获取返回值
     *
     * @param url         url
     * @param headers     头部信息
     * @param paramValues 参数值
     * @param encoding    编码
     * @param method      请求方法(GET , POST , PUT等)
     * @return 返回结果
     */
    public static HttpResult request(String url, List<String> headers, Map<String, String> paramValues, String encoding, String method) {
        HttpURLConnection conn = null;
        try {
            String encodedContent = encodingParams(paramValues, encoding);
            url += (StringUtils.isEmpty(encodedContent)) ? "" : ("?" + encodedContent);

            conn = (HttpURLConnection) new URL(url).openConnection();

            setHeaders(conn, headers, encoding);
            conn.setConnectTimeout(CON_TIME_OUT_MILLIS);
            conn.setReadTimeout(TIME_OUT_MILLIS);
            conn.setRequestMethod(method);
            conn.setDoOutput(true);
            if (POST.equals(method) || PUT.equals(method)) {
                // fix: apache http nio framework must set some content to request body
                byte[] b = encodedContent.getBytes();
                conn.setRequestProperty("Content-Length", String.valueOf(b.length));
                conn.getOutputStream().write(b, 0, b.length);
                conn.getOutputStream().flush();
                conn.getOutputStream().close();
            }
            conn.connect();
            NAMING_LOGGER.debug("Request from server: " + url);
            return getResult(conn);
        } catch (Exception e) {
            try {
                if (conn != null) {
                    NAMING_LOGGER.warn("failed to request " + conn.getURL() + " from "
                        + InetAddress.getByName(conn.getURL().getHost()).getHostAddress());
                }
            } catch (Exception e1) {
                NAMING_LOGGER.error("[NA] failed to request ", e1);
                //ignore
            }

            NAMING_LOGGER.error("[NA] failed to request ", e);

            return new HttpResult(500, e.toString(), Collections.<String, String>emptyMap());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static HttpResult getResult(HttpURLConnection conn) throws IOException {
        int respCode = conn.getResponseCode();

        InputStream inputStream;
        if (HttpURLConnection.HTTP_OK == respCode
            || HttpURLConnection.HTTP_NOT_MODIFIED == respCode
            || Constants.WRITE_REDIRECT_CODE == respCode) {
            inputStream = conn.getInputStream();
        } else {
            inputStream = conn.getErrorStream();
        }

        Map<String, String> respHeaders = new HashMap<String, String>(conn.getHeaderFields().size());
        for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
            respHeaders.put(entry.getKey(), entry.getValue().get(0));
        }

        String encodingGzip = "gzip";

        if (encodingGzip.equals(respHeaders.get(HttpHeaders.CONTENT_ENCODING))) {
            inputStream = new GZIPInputStream(inputStream);
        }

        return new HttpResult(respCode, IoUtils.toString(inputStream, getCharset(conn)), respHeaders);
    }

    private static String getCharset(HttpURLConnection conn) {
        String contentType = conn.getContentType();
        if (StringUtils.isEmpty(contentType)) {
            return "UTF-8";
        }

        String[] values = contentType.split(";");
        if (values.length == 0) {
            return "UTF-8";
        }

        String charset = "UTF-8";
        for (String value : values) {
            value = value.trim();

            if (value.toLowerCase().startsWith("charset=")) {
                charset = value.substring("charset=".length());
            }
        }

        return charset;
    }

    private static void setHeaders(HttpURLConnection conn, List<String> headers, String encoding) {
        if (null != headers) {
            for (Iterator<String> iter = headers.iterator(); iter.hasNext(); ) {
                conn.addRequestProperty(iter.next(), iter.next());
            }
        }

        conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset="
            + encoding);
        conn.addRequestProperty("Accept-Charset", encoding);
    }

    private static String encodingParams(Map<String, String> params, String encoding)
        throws UnsupportedEncodingException {
        if (null == params || params.isEmpty()) {
            return "";
        }

        params.put("encoding", encoding);
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (StringUtils.isEmpty(entry.getValue())) {
                continue;
            }

            sb.append(entry.getKey()).append("=");
            sb.append(URLEncoder.encode(entry.getValue(), encoding));
            sb.append("&");
        }

        if (sb.length() > 0) {
            sb = sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    public static class HttpResult {
        final public int code;
        final public String content;
        final private Map<String, String> respHeaders;

        public HttpResult(int code, String content, Map<String, String> respHeaders) {
            this.code = code;
            this.content = content;
            this.respHeaders = respHeaders;
        }

        public String getHeader(String name) {
            return respHeaders.get(name);
        }
    }
}
