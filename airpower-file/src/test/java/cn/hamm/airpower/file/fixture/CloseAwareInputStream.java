package cn.hamm.airpower.file.fixture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * <h2>测试用输入流：记录自己有没有被关闭</h2>
 *
 * <p>用来回答一个问题：{@code upload()} 传进去的流是谁负责关？
 * {@code ByteArrayInputStream#close()} 是空实现，看不出来，
 * 必须用一个带标志位的流替身。</p>
 *
 * @author Hamm.cn
 */
public class CloseAwareInputStream extends InputStream {
    /**
     * 真实内容
     */
    private final ByteArrayInputStream delegate;

    /**
     * 是否已被关闭
     */
    private boolean closed;

    /**
     * 构造一个带关闭标志的输入流
     *
     * @param content 内容
     */
    public CloseAwareInputStream(byte[] content) {
        this.delegate = new ByteArrayInputStream(content);
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }

    @Override
    public int available() {
        return delegate.available();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        delegate.close();
    }

    /**
     * 是否已被关闭
     *
     * @return 是否已关闭
     */
    public boolean isClosed() {
        return closed;
    }
}
