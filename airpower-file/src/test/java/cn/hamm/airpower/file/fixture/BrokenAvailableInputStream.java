package cn.hamm.airpower.file.fixture;

import java.io.IOException;
import java.io.InputStream;

/**
 * <h2>测试用输入流：{@code available()} 本身就抛 IOException</h2>
 *
 * <p>COS 平台在上传前必须先取一次 {@code available()} 当作 Content-Length，
 * 这一步一旦抛异常，整个上传流程才会走 {@code catch (IOException)} 分支。
 * 普通「读取到一半才失败」的流测不到这个分支，必须专门造一个。</p>
 *
 * @author Hamm.cn
 */
public class BrokenAvailableInputStream extends InputStream {
    /**
     * 提示信息
     */
    public static final String MESSAGE = "模拟流长度未知";

    @Override
    public int available() throws IOException {
        throw new IOException(MESSAGE);
    }

    @Override
    public int read() throws IOException {
        throw new IOException(MESSAGE);
    }
}
