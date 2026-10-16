package cn.hamm.airpower.file.fixture;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <h2>测试用上传文件：每次 {@code getInputStream()} 都返回<b>同一个</b>流实例</h2>
 *
 * <p>基类的 {@code upload(MultipartFile, ...)} 会先读一遍流算 MD5，再取一次流落盘。
 * 真实 Servlet 容器给 {@code MultipartFile} 的实现通常每次返回新流，
 * 但接口契约并没有写死这一点——只返回同一个流时，
 * 第二次拿到的就是<b>已被读空</b>的流，落盘得到 0 字节文件，
 * 文件名却是全量内容的 MD5。这里把该边界显式复现出来。</p>
 *
 * @author Hamm.cn
 */
public class SingleStreamMultipartFile implements MultipartFile {
    /**
     * 文件原始内容
     */
    private final byte[] content;

    /**
     * 共享的流实例，只会被读走一次
     */
    private final ByteArrayInputStream sharedStream;

    /**
     * 原始文件名
     */
    private final String originalFilename;

    /**
     * 构造一个「一次性流」上传文件
     *
     * @param content           文件内容
     * @param originalFilename  原始文件名
     */
    public SingleStreamMultipartFile(byte[] content, String originalFilename) {
        this.content = content.clone();
        this.sharedStream = new ByteArrayInputStream(this.content);
        this.originalFilename = originalFilename;
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return "application/octet-stream";
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content.clone();
    }

    @Override
    public InputStream getInputStream() {
        return sharedStream;
    }

    @Override
    public void transferTo(File dest) throws IOException {
        try (InputStream inputStream = getInputStream();
             OutputStream outputStream = new FileOutputStream(dest)) {
            inputStream.transferTo(outputStream);
        }
    }
}
