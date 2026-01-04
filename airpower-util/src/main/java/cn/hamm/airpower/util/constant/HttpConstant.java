package cn.hamm.airpower.util.constant;

/**
 * <h1>HTTP 常量</h1>
 *
 * @author Hamm.cn
 */
public class HttpConstant {
    public static final String LOCAL_IP_ADDRESS = "127.0.0.1";

    public static final String LOCAL_HOST = "localhost";

    public static class Status {
        public static final int OK = 200;
        public static final int CREATED = 201;
        public static final int NO_CONTENT = 204;
        public static final int MOVED_PERMANENTLY = 301;
        public static final int MOVED_TEMPORARILY = 302;
        public static final int BAD_GATEWAY = 502;
        public static final int NOT_MODIFIED = 304;
        public static final int NOT_ACCEPTABLE = 406;
        public static final int NOT_FOUND = 404;
        public static final int UNAUTHORIZED = 401;
        public static final int INTERNAL_SERVER_ERROR = 500;
        public static final int BAD_REQUEST = 400;
        public static final int UNSUPPORTED_MEDIA_TYPE = 415;
        public static final int FORBIDDEN = 403;
        public static final int SERVICE_UNAVAILABLE = 503;
        public static final int UNPROCESSABLE_ENTITY = 422;
        public static final int TOO_MANY_REQUESTS = 429;
        public static final int GATEWAY_TIMEOUT = 504;
        public static final int REQUEST_TIMEOUT = 408;
        public static final int METHOD_NOT_ALLOWED = 405;
        public static final int NOT_IMPLEMENTED = 501;
    }

    /**
     * 授权类型
     */
    public static class GrantType {
        public static final String PASSWORD = "password";
        public static final String REFRESH_TOKEN = "refresh_token";
        public static final String CLIENT_CREDENTIALS = "client_credentials";
        public static final String AUTHORIZATION_CODE = "authorization_code";
        public static final String IMPLICIT = "implicit";
        public static final String BEARER = "Bearer";
        public static final String BASIC = "Basic";
    }

    /**
     * 请求头
     */
    public static class Header {
        public static final String CONTENT_TYPE = "Content-Type";
        public static final String COOKIE = "Cookie";
        public static final String REQUEST_ID = "X-Request-ID";
        public static final String TRACE_ID = "X-Trace-ID";
        public static final String AUTHORIZATION = "Authorization";
        public static final String USER_AGENT = "User-Agent";
    }

    /**
     * 内容类型
     */
    public static class ContentType {
        public static final String APPLICATION_JSON = "application/json";
        public static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";
        public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
        public static final String MULTIPART_FORM_DATA = "multipart/form-data";
        public static final String TEXT_HTML = "text/html";
        public static final String TEXT_PLAIN = "text/plain";
    }

    /**
     * 代理
     */
    public static class Proxy {
        /**
         * 代理头
         */
        public static class Header {
            public static final String X_FORWARDED_FOR = "X-Forwarded-For";
            public static final String PROXY_CLIENT_IP = "Proxy-Client-IP";
            public static final String WL_PROXY_CLIENT_IP = "WL-Proxy-Client-IP";
            public static final String HTTP_CLIENT_IP = "HTTP_CLIENT_IP";
            public static final String HTTP_X_FORWARDED_FOR = "HTTP_X_FORWARDED_FOR";
        }
    }
}
