package com.claritycam.platform.exception;

import com.claritycam.platform.service.common.OperationalAlertService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private final OperationalAlertService alerts;

  public ApiExceptionHandler(OperationalAlertService alerts) {
    this.alerts = alerts;
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<Map<String, Object>> handleApi(ApiException exception, HttpServletRequest request) {
    return response(exception.getStatus(), exception.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
    String message = exception.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .orElse("Dữ liệu gửi lên không hợp lệ.");
    return response(HttpStatus.BAD_REQUEST, message, request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException exception, HttpServletRequest request) {
    return response(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này.", request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception, HttpServletRequest request) {
    alerts.alert("UNHANDLED_API_ERROR", exception.getClass().getSimpleName() + " at " + request.getRequestURI());
    return response(HttpStatus.INTERNAL_SERVER_ERROR,
        "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.", request);
  }

  private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status).body(Map.of(
        "status", status.value(),
        "message", message,
        "path", request.getRequestURI(),
        "timestamp", Instant.now().toString()));
  }
}
