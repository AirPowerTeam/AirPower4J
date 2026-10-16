package cn.hamm.airpower.file.fixture;

import java.io.IOException;
import java.io.InputStream;

/**
 * <h2>测试用输入流：{@code available()} 与实际长度不一致，且读取抛 IOException</h2>
 *
 * <p>基类的大小校验用的是 {@code available()}，云端平台也用同一个值作为
 * {@code Content-Length}。真实世界里有两种流会让这个值失真：</p>
 * <ul>
 *     <li>未标记长度的流：{@code available()} 恒为 0，默认大小校验形同虚设</li>
 *     <li>中途抛 IOException 的流：读取失败的异常信息必须能透传给调用方</li>
 * </ul>
 *
 * @author Hamm.cn
 */
public class UnreliableInputStream extends InputStream {
    /**
     * 实际内容
     */
    private final byte[] content;

    /**
     * 读取到第几个字节后抛异常，{@code -1} 表示读完也不抛
     */
    private final int failAfter;

    /**
     * 已读字节数
     */
    private int read;

    /**
     * available() 返回的值
     */
    private final int reportedAvailable;

    /**
     * 构造一个不可靠的输入流
     *
     * @param content           实际内容
     * @param reportedAvailable available() 的返回值
     * @param failAfter         读到第几个字节后抛异常，{@code -1} 表示不抛
     */
    public UnreliableInputStream(byte[] content, int reportedAvailable, int failAfter) {
        this.content = content;
        this.reportedAvailable = reportedAvailable;
        this.failAfter = failAfter;
    }

    @Override
    public int read() throws IOException {
        if (failAfter >= 0 && read >= failAfter) {
            throw new IOException("模拟读取中断");
        }
        if (read >= content.length) {
            return -1;
        }
        return content[read++] & 0xFF;
    }

    @Override
    public int available() {
        return reportedAvailable;
    }
}
