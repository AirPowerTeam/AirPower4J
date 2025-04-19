package cn.hamm.airpower.open;

/**
 * <h1>开放应用实体接口</h1>
 *
 * @author Hamm.cn
 * @apiNote 请确保你的应用实体类实现了此接口
 */
public interface IOpenApp {
    /**
     * 获取应用的 AppKey
     *
     * @return AppKey
     */
    String getAppKey();

    /**
     * 获取应用的 AppSecret
     *
     * @return AppSecret
     */
    String getAppSecret();

    /**
     * 获取应用的加密算法
     *
     * @return 算法
     */
    Integer getArithmetic();

    /**
     * 获取应用的私钥
     *
     * @return 私钥
     */
    String getPrivateKey();

    /**
     * 获取应用的公钥
     *
     * @return 公钥
     */
    String getPublicKey();

    /**
     * 获取 IP 白名单列表
     *
     * @return IP 白名单
     */
    String getIpWhiteList();

    /**
     * 是否禁用
     *
     * @return 是否禁用
     */
    Boolean getIsDisabled();
}
