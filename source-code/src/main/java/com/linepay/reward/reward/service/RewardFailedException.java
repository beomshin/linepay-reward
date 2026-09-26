package com.linepay.reward.reward.service;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;

/**
 * 보상 지급 최종 실패 (교정 4).
 * <p>
 * 보상 서비스가 실패 상태(FAILED)를 저장한 뒤 던진다. 트랜잭션은 이 예외에서 롤백하지 않도록 설정해
 * ({@code noRollbackFor}) 실패 상태가 DB 에 남고, 클라이언트에는 정의된 에러 코드로 응답한다.
 */
public class RewardFailedException extends BusinessException {

    public RewardFailedException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
