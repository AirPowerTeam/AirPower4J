package cn.hamm.airpower.util;

import cn.hamm.airpower.util.constant.HttpConstant;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * <h1>Trace 工具类</h1>
 *
 * @author Hamm.cn
 */
public class TraceUtil {

    /**
     * 重置 TraceID
     */
    public static void resetTraceId() {
        setTraceId(null);
    }

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
        if (!StringUtil.hasText(traceId)) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(HttpConstant.Header.TRACE_ID, traceId);
    }
}
