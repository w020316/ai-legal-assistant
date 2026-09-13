package com.lawai.legalassistant.common.exception;

import com.lawai.legalassistant.common.result.Result;
import com.lawai.legalassistant.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GlobalExceptionHandler} 单元测试（v1.18 新增，补覆盖率洼地）
 * <p>
 * 直接调用各 @ExceptionHandler 方法，锁定状态码与业务码映射。
 */
@DisplayName("GlobalExceptionHandler 全局异常处理")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("业务异常 → 200 + 业务码")
    void businessException() {
        ResponseEntity<Result<Void>> resp = handler.handleBusiness(
                BusinessException.of(ResultCode.PARAM_ERROR, "参数错误"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().code()).isEqualTo(ResultCode.PARAM_ERROR);
    }

    @Test
    @DisplayName("参数校验异常 → 400 + PARAM_ERROR")
    void validationExceptions() {
        BeanPropertyBindingResult br = new BeanPropertyBindingResult(new Object(), "req");
        br.addError(new FieldError("req", "title", "标题不能为空"));
        ResponseEntity<Result<Void>> r1 = handler.handleValidation(new MethodArgumentNotValidException(null, br));
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r1.getBody().message()).contains("title");

        ResponseEntity<Result<Void>> r2 = handler.handleBind(new BindException(br));
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Result<Void>> r3 = handler.handleConstraint(new ConstraintViolationException(Set.of()));
        assertThat(r3.getBody().code()).isEqualTo(ResultCode.PARAM_ERROR);
    }

    @Test
    @DisplayName("认证/越权/上传超限/未知异常 → 对应 HTTP 状态码")
    void authAndOthers() {
        assertThat(handler.handleAuth(new AuthenticationException("expired") {
        }).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleAccessDenied(new AccessDeniedException("denied")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleUploadSize(new MaxUploadSizeExceededException(1L)).getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(handler.handleUnknown(new RuntimeException("boom")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
