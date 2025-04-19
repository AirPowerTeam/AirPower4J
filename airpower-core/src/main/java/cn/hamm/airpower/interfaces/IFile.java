package cn.hamm.airpower.interfaces;

/**
 * <h1>标准文件接口</h1>
 *
 * @author Hamm.cn
 */
public interface IFile<E extends IFile<E>> extends IEntity<E> {
    /**
     * 获取文件的 URL
     *
     * @return URL
     */
    String getUrl();

    /**
     * 设置文件的 URL
     *
     * @param url URL
     * @return 实体
     */
    E setUrl(String url);
}
