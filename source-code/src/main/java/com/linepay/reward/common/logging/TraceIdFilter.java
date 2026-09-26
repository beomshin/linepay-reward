package com.linepay.reward.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 단위 로그 추적 필터.
 * <ol>
 *     <li>요청마다 새 traceId 를 만들어 MDC 에 넣는다. → 이 요청에서 남는 모든 로그에 같은 traceId 가 찍힌다.</li>
 *     <li>API 요청·응답(상태 코드, 처리 시간) 로그를 남기고, 응답 헤더 {@code X-Trace-Id} 로 traceId 를 돌려준다.</li>
 *     <li>요청이 끝나면(예외 포함) MDC 에서 traceId 를 제거한다.
 *         WAS 는 스레드를 재사용하므로 제거하지 않으면 다음 요청 로그에 이전 traceId 가 섞인다.</li>
 * </ol>
 * 가장 먼저 실행되도록 최우선 순서로 등록한다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        long startedAt = System.currentTimeMillis();
        String api = request.getMethod() + " " + request.getRequestURI();
        log.info("[REQ] {}", api);
        try {
            chain.doFilter(request, response);
            log.info("[RES] {} status={} elapsed={}ms", api, response.getStatus(), System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            // 전역 예외 핸들러에서 처리되지 못하고 필터까지 올라온 예외
            log.error("[ERR] {} elapsed={}ms", api, System.currentTimeMillis() - startedAt, e);
            throw e;
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
