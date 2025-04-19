package cn.hamm.airpower.open;

/**
 * <h1>开放应用的服务接口</h1>
 *
 * @author Hamm.cn
 * @apiNote 请确保你的开放应用的服务实现了此接口
 */
public interface IOpenAppService {
    /**
     * 通过应用的 AppKey 查一个应用
     *
     * @param appKey AppKey
     * @return 应用
     */
    IOpenApp getByAppKey(String appKey);
}
