package cn.hamm.airpower.util;

import cn.hamm.airpower.request.HttpConstant;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * <h1>Trace工具类</h1>
 *
 * @author Hamm.cn
 */
public class TraceUtil {

    /**
     * 重置TraceID
     */
    public static void resetTraceId() {
        setTraceId(null);
    }

    /**
     * 获取TraceID
     *
     * @return TraceID
     */
    public static String getTraceId() {
        return MDC.get(HttpConstant.Header.TRACE_ID);
    }

    /**
     * 设置TraceID
     *
     * @param traceId TraceID
     */
    public static void setTraceId(@Nullable String traceId) {
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(HttpConstant.Header.TRACE_ID, traceId);
    }
}
