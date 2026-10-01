package cn.hamm.airpower.open;

/**
 * <h1>开放应用的服务接口</h1>
 * 由业务侧提供 {@link IOpenApp} 的查询实现，切面在每次调用 Open API 时通过它取调用方身份。
 *
 * @author Hamm.cn
 * @apiNote 请确保你的开放应用的服务实现了此接口
 */
public interface IOpenAppService {
    /**
     * 通过应用的 AppKey 查一个应用
     *
     * @param appKey AppKey
     * @return 应用，查不到时返回 {@code null}
     */
    IOpenApp getByAppKey(String appKey);
}
