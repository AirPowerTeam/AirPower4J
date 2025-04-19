package cn.hamm.airpower.interceptor.cache;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * <h1>请求体缓存处理类</h1>
 *
 * @author Hamm.cn
 */
public class RequestBodyCacheWrapper extends HttpServletRequestWrapper {
    /**
     * 缓存的请求体字节数组
     */
    private final byte[] cachedBody;

    /**
     * 构造方法
     *
     * @param request 请求
     */
    public RequestBodyCacheWrapper(HttpServletRequest request) throws IOException {
        super(request);
        cachedBody = inputStreamToBytes(request.getInputStream());
    }

    /**
     * 获取请求体输入流
     */
    @Contract(" -> new")
    @Override
    public final @NotNull ServletInputStream getInputStream() {
        return new CachedServletInputStream(new ByteArrayInputStream(cachedBody));
    }

    /**
     * 获取请求阅读器
     */
    @Contract(" -> new")
    @Override
    public final @NotNull BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), UTF_8));
    }

    /**
     * 将输入流转换为字节数组
     *
     * @param inputStream 输入流
     * @return 输入字节数组
     */
    private byte @NotNull [] inputStreamToBytes(@NotNull InputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * 缓存输入流
     */
    private static class CachedServletInputStream extends ServletInputStream {
        /**
         * 输入流
         */
        private final ByteArrayInputStream inputStream;

        /**
         * 构造方法
         *
         * @param inputStream 输入流
         */
        public CachedServletInputStream(ByteArrayInputStream inputStream) {
            this.inputStream = inputStream;
        }

        /**
         * 读取
         */
        @Override
        public final int read() {
            return inputStream.read();
        }

        /**
         * 是否结束
         */
        @Contract(pure = true)
        @Override
        public final boolean isFinished() {
            return false;
        }

        /**
         * 是否就绪
         */
        @Contract(pure = true)
        @Override
        public final boolean isReady() {
            return true;
        }

        /**
         * 设置读取监听器
         *
         * @param readListener 读取监听器
         */
        @Contract(pure = true)
        @Override
        public final void setReadListener(ReadListener readListener) {

        }
    }
}