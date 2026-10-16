package cn.hamm.airpower.file.fixture;

import cn.hamm.airpower.file.AbstractFilePlatformFactory;
import cn.hamm.airpower.file.FilePlatform;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * <h2>测试用文件平台：记录每一次 save 的入参</h2>
 *
 * <p>基类把「命名、哈希、大小校验」都做完了，平台实现只剩一次落盘动作。
 * 这里把落盘换成记录入参，测试就能断言「上传路径是怎么算出来的」，
 * 而不必真的去碰磁盘或云端。</p>
 *
 * @author Hamm.cn
 */
@FilePlatform("TEST")
public class TestFilePlatform extends AbstractFilePlatformFactory {
    /**
     * save 调用次数
     */
    public int saveCount;

    /**
     * 最近一次 save 的目录
     */
    public String lastDirectory;

    /**
     * 最近一次 save 的文件名
     */
    public String lastFileName;

    /**
     * 最近一次 save 收到的字节内容
     */
    public byte[] lastContent;

    /**
     * delete 的入参
     */
    public String deletedPath;

    /**
     * 传给 save 的流在保存后是否已被读完（平台实现一般会整流读入内存）
     */
    public boolean contentReadToEnd;

    /**
     * save 的历史入参，顺序与调用顺序一致
     *
     * @apiNote 用于断言「同一文件重复上传会落到同一路径」
     */
    public final List<String> savedKeys = new ArrayList<>();

    /**
     * 让 save 抛异常的钩子，非 null 时原样抛出
     */
    public RuntimeException saveException;

    @Override
    public void save(InputStream inputStream, String directory, String fileName) {
        if (saveException != null) {
            throw saveException;
        }
        try {
            saveCount++;
            lastDirectory = directory;
            lastFileName = fileName;
            lastContent = inputStream.readAllBytes();
            savedKeys.add(directory + fileName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getUrl(String path, int second) {
        return "test://" + path + "?second=" + second;
    }

    @Override
    public InputStream download(String path) {
        return new ByteArrayInputStream(("downloaded:" + path).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void delete(String path) {
        deletedPath = path;
    }
}
