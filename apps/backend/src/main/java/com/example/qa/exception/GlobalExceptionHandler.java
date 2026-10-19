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

@RestControllerAdvice
public class GlobalExceptionHandler {
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

    @ExceptionHandler(ToolException.class)
    public ResponseEntity<QuestionResponse> tool(ToolException exception) {
        var status = exception.code().equals("DOCUMENT_TOO_LARGE") ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(QuestionResponse.failure(exception.code(), exception.getMessage(), exception.retryable()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<QuestionResponse> unknown(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(QuestionResponse.failure("UNKNOWN_ERROR", "服务暂时不可用", true));
    }
}
