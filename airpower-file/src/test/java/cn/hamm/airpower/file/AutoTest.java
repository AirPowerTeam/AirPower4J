package cn.hamm.airpower.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>自动装配单元测试</h1>
 *
 * <p>这个模块对外「自动可用」全靠两样东西：类上的 {@code @AutoConfiguration}
 * 与 {@code META-INF/spring/...imports} 里的登记。少任何一样，
 * 使用方的容器里就一个 {@link FileHelper} 都没有，而且是启动即失败那种。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("自动装配单元测试")
class AutoTest {

    @Test
    @DisplayName("标注了 @AutoConfiguration")
    void annotatedAsAutoConfiguration() {
        assertNotNull(Auto.class.getAnnotation(AutoConfiguration.class),
                "没有这个注解时不会被当作自动配置类，宿主的自动装配列表里根本不会有本模块");
    }

    @Test
    @DisplayName("组件扫描范围是本模块的包")
    void componentScanScope() {
        ComponentScan componentScan = Auto.class.getAnnotation(ComponentScan.class);
        assertNotNull(componentScan, "本模块的 Bean 靠组件扫描发现，没有 @Import 逐个登记");
        assertEquals(List.of("cn.hamm.airpower.file"),
                Arrays.stream(componentScan.value()).toList(),
                "扫描范围必须正好覆盖本包，少了平台实现就注册不上，多了会扫到别的模块的 Bean");
    }

    @Test
    @DisplayName("自动装配清单里登记了本类")
    void registeredInImports() throws IOException {
        List<String> lines;
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertNotNull(inputStream, "缺少 AutoConfiguration.imports，宿主应用不会自动装配本模块");
            lines = Arrays.stream(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        }
        assertTrue(lines.contains(Auto.class.getName()),
                "清单内容为 " + lines + "，必须包含 " + Auto.class.getName());
        assertEquals(1, lines.size(), "清单里只应登记本模块这一个自动配置类，实际为 " + lines);
    }
}
