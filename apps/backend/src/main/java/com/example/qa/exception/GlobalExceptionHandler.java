package com.example.qa.exception;

import com.example.qa.model.QuestionResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<QuestionResponse> unknown(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(QuestionResponse.failure("UNKNOWN_ERROR", "服务暂时不可用", true));
    }
}
