package cn.hamm.airpower.util;

import cn.hamm.airpower.util.exception.ServiceException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;

/**
 * <h1>AES 工具类</h1>
 *
 * @author Hamm.cn
 */
public class AesUtil {
    /**
     * 密钥长度
     */
    private static final int KEY_SIZE = 256;

    /**
     * AES
     */
    private static final String AES = "AES";

    /**
     * {@code AES/CBC/PKCS5Padding}
     */
    private static final String AES_CBC_PKCS5_PADDING = "AES/CBC/PKCS5Padding";

    /**
     * 密钥
     */
    private byte[] key;

    /**
     * 偏移向量
     */
    private byte[] iv = "0000000000000000".getBytes(UTF_8);

    /**
     * 算法
     */
    private String algorithm = AES_CBC_PKCS5_PADDING;

    /**
     * 禁止外部实例化
     */
    private AesUtil() {

    }

    /**
     * 创建实例
     *
     * @return 新实例
     */
    @Contract(" -> new")
    public static @NotNull AesUtil create() {
        return new AesUtil();
    }

    /**
     * 获取随机密钥
     *
     * @return 随机密钥
     */
    @Contract(" -> new")
    public static @NotNull String getRandomKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(AES);
            keyGen.init(KEY_SIZE, new SecureRandom());
            SecretKey aesKey = keyGen.generateKey();
            byte[] keyBytes = aesKey.getEncoded();
            return Base64.getEncoder().encodeToString(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException(e);
        }
    }

    /**
     * 获取随机向量
     *
     * @return 随机向量
     */
    @Contract(" -> new")
    public static @NotNull String getRandomIv() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(AES);
            keyGen.init(KEY_SIZE / 2, new SecureRandom());
            SecretKey aesKey = keyGen.generateKey();
            byte[] keyBytes = aesKey.getEncoded();
            return Base64.getEncoder().encodeToString(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException(e);
        }
    }

    /**
     * 设置偏移向量
     *
     * @param iv 偏移向量
     * @return <b>AesUtil</b> 实例
     */
    @Contract("_ -> this")
    public final AesUtil setIv(String iv) {
        this.iv = Base64.getDecoder().decode(iv);
        return this;
    }

    /**
     * 设置算法
     *
     * @param algorithm 算法
     * @return <b>AesUtil</b> 实例
     */
    @Contract(value = "_ -> this", mutates = "this")
    public final AesUtil setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    /**
     * 设置密钥
     *
     * @param key 密钥
     * @return <b>AesUtil</b> 实例
     */
    @Contract("_ -> this")
    public final AesUtil setKey(String key) {
        this.key = Base64.getDecoder().decode(key);
        return this;
    }

    /**
     * 加密
     *
     * @param source 待加密的内容
     * @return 加密后的内容
     */
    public final String encrypt(String source) {
        try {
            return Base64.getEncoder().encodeToString(getCipher(ENCRYPT_MODE)
                    .doFinal(source.getBytes(UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 解密
     *
     * @param content 加密后的内容
     * @return 解密后的内容
     */
    @Contract("_ -> new")
    public final @NotNull String decrypt(String content) {
        try {
            return new String(getCipher(DECRYPT_MODE)
                    .doFinal(Base64.getDecoder().decode(content)), UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取 {@code Cipher}
     *
     * @param mode 模式
     * @return {@code Cipher}
     */
    private @NotNull Cipher getCipher(int mode) {
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, AES);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(mode, secretKeySpec, ivParameterSpec);
            return cipher;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
