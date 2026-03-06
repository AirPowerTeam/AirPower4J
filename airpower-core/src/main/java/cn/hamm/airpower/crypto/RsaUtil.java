package cn.hamm.airpower.crypto;

import cn.hamm.airpower.exception.ServiceException;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static cn.hamm.airpower.exception.ServiceError.SERVICE_ERROR;

/**
 * <h1>RSA 工具类</h1>
 *
 * @author Hamm.cn
 */
@Slf4j
@Accessors(chain = true)
public class RsaUtil {
    /**
     * RSA
     */
    private static final String RSA = "RSA";

    /**
     * 加密算法 KEY 长度
     */
    @Setter
    private int keySize = 2048;

    @Setter
    private String cryptAlgorithm = RSA;

    @Setter
    private String signAlgorithm = "SHA256withRSA";

    /**
     * 公钥
     */
    @Setter
    private String publicKey;

    /**
     * 私钥
     */
    @Setter
    private String privateKey;

    /**
     * 禁止外部实例化
     */
    @Contract(pure = true)
    private RsaUtil() {
    }

    /**
     * 创建实例
     *
     * @return 实例
     */
    @Contract(value = " -> new", pure = true)
    public static @NotNull RsaUtil create() {
        return new RsaUtil();
    }

    /**
     * 获取一个公钥
     *
     * @param publicKeyString 公钥字符串
     * @return 公钥
     * @throws Exception 异常
     */
    public PublicKey getPublicKey(String publicKeyString) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(cryptAlgorithm);
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyString));
        return keyFactory.generatePublic(x509EncodedKeySpec);
    }

    /**
     * 生成 RSA 密钥对 {@code 2048}
     *
     * @return {@code KeyPair}
     */
    public KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(cryptAlgorithm);
        keyPairGenerator.initialize(keySize);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * 将公钥转换为 PEM 格式
     *
     * @param publicKey 公钥
     * @return PEM
     */
    public @NotNull String convertPublicKeyToPem(@NotNull PublicKey publicKey) {
        String base64Encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" +
                wrapBase64Text(base64Encoded) +
                "-----END PUBLIC KEY-----";
    }


    /**
     * 从密钥对获取 PEM 格式公钥
     *
     * @param keyPair 密钥对
     * @return PEM 公钥
     */
    public @NotNull String getPemPublicKey(@NotNull KeyPair keyPair) {
        return convertPublicKeyToPem(keyPair.getPublic());
    }

    /**
     * 将私钥转换为 PEM 格式
     *
     * @param privateKey 私钥
     * @return PEM
     */
    public @NotNull String convertPrivateKeyToPem(@NotNull PrivateKey privateKey) {
        String base64Encoded = Base64.getEncoder().encodeToString(privateKey.getEncoded());
        return "-----BEGIN RSA PRIVATE KEY-----\n" +
                wrapBase64Text(base64Encoded) +
                "-----END RSA PRIVATE KEY-----";
    }


    /**
     * 从密钥对获取 PEM 格式私钥
     *
     * @param keyPair 密钥对
     * @return PEM 私钥
     */
    public @NotNull String getPemPrivateKey(@NotNull KeyPair keyPair) {
        return convertPrivateKeyToPem(keyPair.getPrivate());
    }

    /**
     * 将 {@code Base64} 编码的文本换行
     *
     * @param base64Text 原始 {@code Base64}
     * @return 换行后的
     */
    public @NotNull String wrapBase64Text(@NotNull String base64Text) {
        final int wrapLength = 64;
        StringBuilder wrappedText = new StringBuilder();
        int start = 0;
        while (start < base64Text.length()) {
            int end = Math.min(start + wrapLength, base64Text.length());
            wrappedText.append(base64Text, start, end).append("\n");
            start = end;
        }
        return wrappedText.toString();
    }

    /**
     * 获取一个私钥
     *
     * @param privateKeyString 私钥字符串
     * @return 私钥
     * @throws Exception 异常
     */
    public @NotNull PrivateKey getPrivateKey(String privateKeyString) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(cryptAlgorithm);
        PKCS8EncodedKeySpec private8KeySpec =
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyString));
        return keyFactory.generatePrivate(private8KeySpec);
    }

    /**
     * 公钥加密
     *
     * @param sourceContent 原文
     * @return 密文
     */
    public final String publicKeyEncrypt(String sourceContent) {
        try {
            int blockSize = keySize / 8 - 11;
            return encrypt(sourceContent, getPublicKey(publicKey), blockSize);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ServiceException(exception);
        }
    }

    /**
     * 私钥解密
     *
     * @param encryptedContent 密文
     * @return 原文
     */
    public final @NotNull String privateKeyDecrypt(String encryptedContent) {
        try {
            int blockSize = keySize / 8;
            return decrypt(encryptedContent, getPrivateKey(privateKey), blockSize);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ServiceException(exception);
        }
    }

    /**
     * 私钥加密
     *
     * @param sourceContent 原文
     * @return 密文
     */
    public final String privateKeyEncrypt(String sourceContent) {
        try {
            int blockSize = keySize / 8 - 11;
            return encrypt(sourceContent, getPrivateKey(privateKey), blockSize);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ServiceException(exception);
        }
    }

    /**
     * 私钥签名
     *
     * @param sourceContent 原文
     * @return 签名
     */
    public final String privateKeySignature(String sourceContent) {
        try {
            return sign(sourceContent, getPrivateKey(privateKey));
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ServiceException(exception);
        }
    }

    /**
     * 公钥验签
     *
     * @param sourceContent 原文
     * @param signature     签名
     * @return 是否成功
     */
    public final boolean publicKeyVerifySignature(String sourceContent, String signature) {
        try {
            return verify(sourceContent, signature, getPublicKey(publicKey));
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ServiceException(exception);
        }
    }

    /**
     * 公钥解密
     *
     * @param encryptedContent 密文
     * @return 原文
     */
    public final @NotNull String publicKeyDecrypt(String encryptedContent) {
        try {
            int blockSize = keySize / 8;
            return decrypt(encryptedContent, getPublicKey(publicKey), blockSize);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            throw new ServiceException(exception);
        }
    }

    /**
     * 公私钥解密
     *
     * @param encryptedContent 密文
     * @param key              公私钥
     * @param blockSize        分块大小
     * @return 明文
     */
    @Contract("_, _, _ -> new")
    private @NotNull String decrypt(String encryptedContent, Key key, int blockSize) throws Exception {
        Cipher deCipher = Cipher.getInstance(cryptAlgorithm);
        deCipher.init(Cipher.DECRYPT_MODE, key);
        byte[] resultBytes = rsaDoFinal(deCipher, Base64.getDecoder().decode(encryptedContent), blockSize);
        return new String(resultBytes);
    }

    /**
     * 公私钥加密
     *
     * @param sourceContent 明文
     * @param key           公私钥
     * @param blockSize     区块大小
     * @return 密文
     */
    private String encrypt(@NotNull String sourceContent, Key key, int blockSize) throws Exception {
        Cipher cipher = Cipher.getInstance(cryptAlgorithm);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] resultBytes = rsaDoFinal(cipher, sourceContent.getBytes(), blockSize);
        return Base64.getEncoder().encodeToString(resultBytes);
    }

    /**
     * 使用私钥对数据进行签名
     *
     * @param sourceContent 明确问
     * @param privateKey    签名用的私钥
     * @return 签名结果字节数组
     */
    private String sign(@NotNull String sourceContent, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance(signAlgorithm);
        signature.initSign(privateKey);
        signature.update(sourceContent.getBytes(StandardCharsets.UTF_8));
        byte[] resultBytes = signature.sign();
        return Base64.getEncoder().encodeToString(resultBytes);
    }

    /**
     * 使用公钥验证签名
     *
     * @param sourceContent   原始数据
     * @param sourceSignature 签名字符串
     * @param publicKey       验证用的公钥
     * @return 验证是否通过
     */
    private boolean verify(@NotNull String sourceContent, @NotNull String sourceSignature, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance(signAlgorithm);
        signature.initVerify(publicKey);
        signature.update(sourceContent.getBytes(StandardCharsets.UTF_8));
        return signature.verify(Base64.getDecoder().decode(sourceSignature));
    }

    /**
     * RSA 处理方法
     *
     * @param cipher      RSA 实例
     * @param sourceBytes 加解密原始数据
     * @param blockSize   分片大小
     * @return 加解密结果
     * @throws Exception 加解密异常
     */
    private byte @NotNull [] rsaDoFinal(Cipher cipher, byte @NotNull [] sourceBytes, int blockSize) throws Exception {
        SERVICE_ERROR.when(blockSize <= 0, "分段大小必须大于0");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int inputLength = sourceBytes.length;
        int currentOffSet = 0;
        byte[] cacheBytes;
        int index = 0;
        // 对数据分段解密
        while (inputLength - currentOffSet > 0) {
            cacheBytes = cipher.doFinal(sourceBytes, currentOffSet, Math.min(inputLength - currentOffSet, blockSize));
            byteArrayOutputStream.write(cacheBytes, 0, cacheBytes.length);
            index++;
            currentOffSet = index * blockSize;
        }
        byte[] data = byteArrayOutputStream.toByteArray();
        byteArrayOutputStream.close();
        return data;
    }
}
