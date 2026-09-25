package com.linepay.reward.common.time;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * KST 시간 유틸리티.
 * <p>
 * 과제 9절 및 프롬프트 규칙에 따라 일자와 시간을 컬럼(필드)으로 나눠 관리한다.
 * <ul>
 *     <li>일자: {@code yyyyMMdd} (예: 20260923)</li>
 *     <li>시간: {@code HHmmss}   (예: 143005)</li>
 * </ul>
 * 모든 일자/시간 값은 KST(Asia/Seoul) 기준이다.
 */
public final class KstTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private KstTime() {
    }

    /** 주어진 Clock 기준 현재 KST 일시 (초 단위 절삭: 저장 형식이 HHmmss 이므로) */
    public static LocalDateTime now(Clock clock) {
        return LocalDateTime.now(clock.withZone(ZONE)).withNano(0);
    }

    /** LocalDateTime -> yyyyMMdd */
    public static String toDate(LocalDateTime dateTime) {
        return dateTime.format(DATE_FORMAT);
    }

    /** LocalDateTime -> HHmmss */
    public static String toTime(LocalDateTime dateTime) {
        return dateTime.format(TIME_FORMAT);
    }

    /** yyyyMMdd + HHmmss -> LocalDateTime */
    public static LocalDateTime of(String date, String time) {
        return LocalDateTime.of(
                java.time.LocalDate.parse(date, DATE_FORMAT),
                java.time.LocalTime.parse(time, TIME_FORMAT));
    }
}
