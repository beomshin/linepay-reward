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
    @DisplayName("QA-L06 정상 요청: 요청·DB 조회·비즈니스·응답 로그에 같은 traceId 가 찍히고, 요청 후 MDC 가 비워진다")
    void sameTraceIdInRequestLogs(CapturedOutput output) throws Exception {
        String traceId = mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andReturn().getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);

        List<String> lines = linesOf(output, traceId);
        assertThat(lines).anyMatch(l -> l.contains("[REQ] 요청 시작 - POST /linepay/v1/mission/USER_0001/MISSION_0003/complete"));
        assertThat(lines).anyMatch(l -> l.contains("[USER] 사용자 조회 userId=USER_0001 존재여부=true"));
        assertThat(lines).anyMatch(l -> l.contains("[MISSION] 미션 락 획득 missionId=MISSION_0003"));
        assertThat(lines).anyMatch(l -> l.contains("[MISSION] 참여 조건 검사 missionId=MISSION_0003") && l.contains("결과=참여가능"));
        assertThat(lines).anyMatch(l -> l.contains("[MISSION] 참여 이력 저장 완료"));
        assertThat(lines).anyMatch(l -> l.contains("[RES] 요청 종료 - POST /linepay/v1/mission/USER_0001/MISSION_0003/complete 상태=200"));
        assertThat(MDC.get(TraceIdFilter.TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("QA-L07 예외 요청: 예외 로그에도 같은 traceId 가 찍힌다")
    void sameTraceIdInExceptionLog(CapturedOutput output) throws Exception {
        String traceId = mockMvc.perform(get("/linepay/v1/mission/USER_9999"))
                .andReturn().getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);

        List<String> lines = linesOf(output, traceId);
        assertThat(lines).anyMatch(l -> l.contains("[USER] 사용자 조회 userId=USER_9999 존재여부=false"));
        assertThat(lines).anyMatch(l -> l.contains("[EXC] 비즈니스 거절: USER_NOT_FOUND (사용자를 찾을 수 없습니다.)"));
        assertThat(lines).anyMatch(l -> l.contains("상태=404"));
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
        assertThat(linesOf(output, first)).anyMatch(l -> l.contains("[MISSION] 참여 이력 저장 완료"))
                .noneMatch(l -> l.contains("MISSION_REENTRY_COOLDOWN"));
        assertThat(linesOf(output, second)).anyMatch(l -> l.contains("[MISSION] 참여 불가") && l.contains("MISSION_REENTRY_COOLDOWN"))
                .noneMatch(l -> l.contains("[MISSION] 참여 이력 저장 완료"));
    }

    @Test
    @DisplayName("QA-L09 보상 지급 요청: DB 조회·보상 선정·저장 단계 로그가 한 traceId 로 이어진다")
    void rewardFlowLogs(CapturedOutput output) throws Exception {
        String body = mockMvc.perform(post("/linepay/v1/mission/USER_0001/MISSION_0003/complete"))
                .andReturn().getResponse().getContentAsString();
        String participationId = body.replaceAll(".*\"participationId\":(\\d+).*", "$1");

        String traceId = mockMvc.perform(post("/linepay/v1/reward/USER_0001/" + participationId))
                .andReturn().getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);

        List<String> lines = linesOf(output, traceId);
        assertThat(lines).anyMatch(l -> l.contains("[REWARD] 보상 지급 요청 시작"));
        assertThat(lines).anyMatch(l -> l.contains("[REWARD] 참여 이력 락 획득 participationId=" + participationId));
        assertThat(lines).anyMatch(l -> l.contains("[REWARD] 기존 보상 결과 조회") && l.contains("상태=없음"));
        assertThat(lines).anyMatch(l -> l.contains("[REWARD] 보상 아이템 조회 missionId=MISSION_0003"));
        assertThat(lines).anyMatch(l -> l.contains("[REWARD] 포인트 지급 결정"));
        assertThat(lines).anyMatch(l -> l.contains("[REWARD] 보상 결과 저장 완료") && l.contains("상태=GRANTED"));
    }
}
