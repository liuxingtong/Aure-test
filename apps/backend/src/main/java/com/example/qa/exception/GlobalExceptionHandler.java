package com.example.qa.exception;

import com.example.qa.model.QuestionResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.example.qa.tools.ToolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<QuestionResponse> validation(MethodArgumentNotValidException exception) {
        var message = exception.getBindingResult().getFieldErrors().stream().map(error -> error.getField() + ": " + error.getDefaultMessage()).collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(QuestionResponse.failure("VALIDATION_ERROR", message, false));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<QuestionResponse> constraint(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(QuestionResponse.failure("VALIDATION_ERROR", exception.getMessage(), false));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<QuestionResponse> malformed(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(QuestionResponse.failure("VALIDATION_ERROR", "请求 JSON 格式无效", false));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<QuestionResponse> notFound(org.springframework.web.servlet.resource.NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(QuestionResponse.failure("NOT_FOUND", "接口不存在", false));
    }

    @ExceptionHandler(ToolException.class)
    public ResponseEntity<QuestionResponse> tool(ToolException exception) {
        var status = exception.code().equals("DOCUMENT_TOO_LARGE") ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(QuestionResponse.failure(exception.code(), exception.getMessage(), exception.retryable()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<QuestionResponse> unknown(Exception exception) {
        // 以前这里静默吞掉异常，provider 422 之类的根因只能去 provider 侧找。
        log.error("unhandled request error type={} message={}", exception.getClass().getSimpleName(), exception.getMessage(), exception);
        // 参数/协议类错误重试也不会成功，避免 Electron 对确定性 500 再打三次。
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(QuestionResponse.failure("UNKNOWN_ERROR", "服务暂时不可用", false));
    }
}
