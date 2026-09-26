package com.linepay.reward.unit;

import com.linepay.reward.common.logging.TraceIdFilter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 요청 단위 MDC(traceId) 설정·정리 검증 (교정 2)
 */
@DisplayName("[QA-L] 요청 추적 필터 (MDC traceId)")
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    @DisplayName("QA-L01 요청 처리 중 MDC 에 traceId 가 있고, 응답 헤더 X-Trace-Id 와 같다")
    void traceIdDuringRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seen = new String[1];

        filter.doFilter(new MockHttpServletRequest("GET", "/linepay/v1/mission/USER_0001"), response,
                (req, res) -> seen[0] = MDC.get(TraceIdFilter.TRACE_ID));

        assertThat(seen[0]).isNotBlank().hasSize(32);
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(seen[0]);
    }

    @Test
    @DisplayName("QA-L02 요청이 끝나면 MDC 에서 traceId 가 제거된다")
    void clearedAfterRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), (req, res) -> { });

        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("QA-L03 처리 중 예외가 나도 MDC 에서 traceId 가 제거되고 예외는 그대로 전달된다")
    void clearedAfterException() {
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(),
                (req, res) -> { throw new ServletException("boom"); }))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("QA-L04 요청마다 서로 다른 traceId 가 발급된다")
    void differentPerRequest() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/x"), first, (req, res) -> { });
        filter.doFilter(new MockHttpServletRequest("GET", "/x"), second, (req, res) -> { });

        assertThat(first.getHeader(TraceIdFilter.TRACE_ID_HEADER))
                .isNotEqualTo(second.getHeader(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("QA-L05 동시 요청 50건: 요청 안에서는 traceId 가 유지되고, 요청 간에는 섞이지 않는다 (스레드 재사용 포함)")
    void notMixedAcrossConcurrentRequests() throws Exception {
        int requests = 50;
        // 스레드 수 < 요청 수 → 스레드가 재사용되는 WAS 환경과 같은 조건
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String[]>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                Callable<String[]> task = () -> {
                    start.await();
                    String before = MDC.get(TraceIdFilter.TRACE_ID);   // 요청 시작 전 (이전 요청 값이 남아 있으면 안 됨)
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    String[] inside = new String[2];
                    filter.doFilter(new MockHttpServletRequest("GET", "/x"), response, (req, res) -> {
                        inside[0] = MDC.get(TraceIdFilter.TRACE_ID);
                        LockSupport.parkNanos(5_000_000);                 // 5ms: 다른 요청과 처리 시간이 겹치도록
                        inside[1] = MDC.get(TraceIdFilter.TRACE_ID);
                    });
                    return new String[]{before, inside[0], inside[1], response.getHeader(TraceIdFilter.TRACE_ID_HEADER)};
                };
                futures.add(executor.submit(task));
            }
            start.countDown();

            Set<String> traceIds = new HashSet<>();
            for (Future<String[]> future : futures) {
                String[] r = future.get(30, TimeUnit.SECONDS);
                assertThat(r[0]).as("요청 시작 전 MDC").isNull();
                assertThat(r[1]).as("요청 처리 중 traceId 유지").isEqualTo(r[2]).isEqualTo(r[3]);
                traceIds.add(r[1]);
            }
            assertThat(traceIds).as("요청마다 고유한 traceId").hasSize(requests);
        } finally {
            executor.shutdownNow();
        }
    }
}
