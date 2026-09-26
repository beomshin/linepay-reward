package com.linepay.reward.api;

import com.linepay.reward.common.logging.TraceIdFilter;
import com.linepay.reward.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * API 요청·응답·예외 로그에 같은 traceId 가 남는지 검증 (교정 2)
 * local 프로파일(콘솔 로그)로 실행되며, 콘솔 출력을 캡처해 확인한다.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("[QA-L] API 로그 traceId 연결")
class TraceIdLoggingTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    /** 해당 traceId 가 찍힌 로그 라인 */
    private List<String> linesOf(CapturedOutput output, String traceId) {
        return output.getOut().lines().filter(line -> line.contains("[" + traceId + "]")).toList();
    }

    @Test
    @DisplayName("QA-L06 정상 요청: 요청·비즈니스·응답 로그에 같은 traceId 가 찍히고, 요청 후 MDC 가 비워진다")
    void sameTraceIdInRequestLogs(CapturedOutput output) throws Exception {
        String traceId = mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andReturn().getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);

        List<String> lines = linesOf(output, traceId);
        assertThat(lines).anyMatch(l -> l.contains("[REQ] POST /linepay/v1/mission/USER_0001/MISSION_0003/complete"));
        assertThat(lines).anyMatch(l -> l.contains("[MISSION] completed"));
        assertThat(lines).anyMatch(l -> l.contains("[RES] POST /linepay/v1/mission/USER_0001/MISSION_0003/complete status=200"));
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("QA-L07 예외 요청: 예외 로그에도 같은 traceId 가 찍힌다")
    void sameTraceIdInExceptionLog(CapturedOutput output) throws Exception {
        String traceId = mockMvc.perform(get("/linepay/v1/mission/USER_9999"))
                .andReturn().getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);

        List<String> lines = linesOf(output, traceId);
        assertThat(lines).anyMatch(l -> l.contains("[EXC] business rejected: USER_NOT_FOUND"));
        assertThat(lines).anyMatch(l -> l.contains("status=404"));
    }

    @Test
    @DisplayName("QA-L08 연속된 두 요청의 로그는 서로 다른 traceId 로 구분되고 섞이지 않는다")
    void differentRequestsNotMixed(CapturedOutput output) throws Exception {
        String first = mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andReturn().getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);
        String second = mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andReturn().getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);

        assertThat(first).isNotEqualTo(second);
        // 첫 요청은 성공, 두 번째는 1시간 재참여 제한 → 각 결과 로그가 자기 traceId 에만 있어야 한다
        assertThat(linesOf(output, first)).anyMatch(l -> l.contains("[MISSION] completed"))
                .noneMatch(l -> l.contains("MISSION_REENTRY_COOLDOWN"));
        assertThat(linesOf(output, second)).anyMatch(l -> l.contains("MISSION_REENTRY_COOLDOWN"))
                .noneMatch(l -> l.contains("[MISSION] completed"));
    }
}
