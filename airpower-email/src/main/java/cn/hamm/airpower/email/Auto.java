package cn.hamm.airpower.email;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * <h1>邮件模块自动装配</h1>
 *
 * @author Hamm.cn
 * @apiNote 由 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 声明引入，依赖本模块即自动生效，业务方无需手工扫描 {@code cn.hamm.airpower.email} 包
 */
@AutoConfiguration
@ComponentScan("cn.hamm.airpower.email")
public class Auto {

}
