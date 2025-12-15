package cn.hamm.airpower.util;

import cn.hamm.airpower.request.HttpConstant;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.UUID;

/**
 * <h1>TraceUtil</h1>
 *
 * @author Hamm.cn
 */
public class TraceUtil {
    /**
     * 获取 TraceID
     *
     * @return TraceID
     */
    public static String getTraceId() {
        return MDC.get(HttpConstant.Header.TRACE_ID);
    }

    /**
     * 设置 TraceID
     *
     * @param traceId TraceID
     */
    public static void setTraceId(@Nullable String traceId) {
        if (Objects.isNull(traceId)) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(HttpConstant.Header.TRACE_ID, traceId);
    }

    /**
     * 重置 TraceID
     */
    public static void resetTraceId() {
        setTraceId(null);
    }
}
