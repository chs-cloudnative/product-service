package com.chs.productservice.config;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 處理業務邏輯異常（IllegalArgumentException）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Business logic error: {}", e.getMessage());

        HttpStatus status = determineHttpStatus(e.getMessage());
        return ResponseEntity.status(status)
                .body(Map.of("error", e.getMessage()));
    }

    /**
     * 處理驗證錯誤 Controller 可以省略使用 Binding Result
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException e) {
        log.warn("Validation error occurred");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildValidationErrorResponse(e.getBindingResult()));
    }


    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadableErrors(NoResourceFoundException e) {
        log.warn("Wrong URL");

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }


    // Body JSON不合規語法 ｜ 參數使用無效格式 ｜ 嘗試修改dto以外的attribute
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, UnrecognizedPropertyException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequestExceptions(Exception e) {
        log.warn("Bad request occurred: {}", e.getClass().getSimpleName());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }


    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not allowed: {}", e.getMethod());

        Map<String, Object> errorResponse = new LinkedHashMap<>();
        errorResponse.put("error", "Method Not Allowed");

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(errorResponse);
    }

    /**
     * 處理所有其他未捕獲的異常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        log.error("Unexpected error occurred", e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }

    /**
     * 根據錯誤訊息判斷適當的 HTTP 狀態碼, 錯誤訊息參考 Services
     */
    private HttpStatus determineHttpStatus(String message) {
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("not found")) {
            return HttpStatus.NOT_FOUND;
        } else if (lowerMessage.contains("access denied") ||
                lowerMessage.contains("can only update their own") ||
                lowerMessage.contains("can only view their own")) {
            return HttpStatus.FORBIDDEN;
        } else if (lowerMessage.contains("already exists")) {
            return HttpStatus.BAD_REQUEST;
        } else {
            return HttpStatus.BAD_REQUEST;
        }
    }

    /**
     * 建立驗證錯誤回應
     * {
     *      "error": "Validation failed",
     *      "fieldErrors": { ... }
     * }
     */

    private Map<String, Object> buildValidationErrorResponse(BindingResult bindingResult) {
        Map<String, Object> errorResponse = new LinkedHashMap<>();
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        bindingResult.getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        errorResponse.put("error", "Validation failed");
        errorResponse.put("fieldErrors", fieldErrors);

        return errorResponse;
    }
}
