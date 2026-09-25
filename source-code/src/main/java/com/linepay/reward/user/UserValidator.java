package com.linepay.reward.user;

import com.linepay.reward.common.exception.BusinessException;
import com.linepay.reward.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 요청 사용자 검증.
 * <p>
 * 과제 11절에 따라 인증은 구현하지 않고, PathVariable 로 전달된 userId 의 존재 여부만 확인한다.
 */
@RequiredArgsConstructor
@Component
public class UserValidator {

    private final UserRepository userRepository;

    public void validateExists(String userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
