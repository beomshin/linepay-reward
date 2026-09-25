package com.linepay.reward;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LINE Pay 리워드 서비스 애플리케이션 진입점.
 * <p>
 * 사용자가 미션을 수행하면 포인트 또는 쿠폰을 지급하는 백엔드 서비스이다.
 */
@SpringBootApplication
public class LinepayRewardApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinepayRewardApplication.class, args);
    }
}
