package com.marketplace.backend.exception;

import com.marketplace.backend.service.ErrorMonitorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@lombok.extern.slf4j.Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorMonitorService errorMonitor;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        return buildResponse(ex.getMessage(), ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errors.put(err.getField(), err.getDefaultMessage()));

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    // an unknown URL / wrong HTTP method is the caller's mistake, not a server error
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex) {
        return buildResponse("Không tìm thấy đường dẫn", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return buildResponse("Phương thức không được hỗ trợ", HttpStatus.METHOD_NOT_ALLOWED);
    }

    // malformed JSON, a missing / wrongly typed parameter: also the caller's mistake
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        String message = ex instanceof MissingServletRequestParameterException m ? "Thiếu tham số " + m.getParameterName()
                : ex instanceof MethodArgumentTypeMismatchException m ? "Tham số " + m.getName() + " không hợp lệ"
                : "Dữ liệu gửi lên không hợp lệ";
        return buildResponse(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(AccessDeniedException ex) {
        return buildResponse("Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex) {
        return buildResponse("Tệp tải lên quá lớn", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /** Anything unexpected: recorded for the admins (Giam sat loi); the user gets a reference, not internals. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest request) {
        String ref = errorMonitor.record(ex, request.getMethod(), request.getRequestURI());
        log.error("Unhandled error {} on {} {}", ref, request.getMethod(), request.getRequestURI(), ex);
        ResponseEntity<Map<String, Object>> r = buildResponse("Đã có lỗi xảy ra, vui lòng thử lại sau." + (ref != null ? " Mã lỗi: " + ref : ""),
                HttpStatus.INTERNAL_SERVER_ERROR);
        if (ref != null) r.getBody().put("errorCode", ref);
        return r;
    }

    private ResponseEntity<Map<String, Object>> buildResponse(String message, HttpStatus status) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
