package cn.hamm.airpower.open;

/**
 * <h2>开放应用实体接口</h2>
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
     * 是否加密应用
     *
     * @return {@code true} 加密
     */
    default Boolean getIsEncrypted() {
        return false;
    }

    /**
     * 获取应用的公钥
     *
     * @return 公钥
     */
    default String getPublicKey() {
        return "";
    }

    /**
     * 获取 IP 白名单列表
     *
     * @return IP 白名单，多个 IP 用空格分开，空表示不限制
     */
    default String getIpWhiteList() {
        return "";
    }

    /**
     * 是否禁用
     *
     * @return {@code true} 已禁用，禁用后所有调用都会被拒绝
     */
    default Boolean getIsDisabled() {
        return false;
    }
}
