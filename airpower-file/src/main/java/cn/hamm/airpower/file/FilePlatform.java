package cn.hamm.airpower.file;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>文件存储平台</h1>
 * 标记一个类为文件存储平台，被标记的实例会被自动注册到 {@link FileHelper} 中。
 *
 * @author Hamm.cn
 */
@Target(TYPE)
@Retention(RUNTIME)
@Inherited
@Documented
@Component
public @interface FilePlatform {
    /**
     * 平台唯一标识
     */
    String value();
}
