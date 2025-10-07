package com.reliaquest.api.advice;

import com.reliaquest.api.model.ApiResponse;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler for the API. Provides consistent error responses across all endpoints.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** Handle 404 Not Found - Resource doesn't exist */
  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException ex) {
    log.warn("Resource not found: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResponse.error("Resource not found: " + ex.getMessage()));
  }

  /** Handle 400 Bad Request - Validation failures */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String errorMsg =
        ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));

    if (errorMsg.isEmpty()) {
      errorMsg = "Validation failed";
    }

    log.warn("Validation error: {}", errorMsg);
    return ResponseEntity.badRequest().body(ApiResponse.error(errorMsg));
  }

  /** Handle 400 Bad Request - Invalid arguments (e.g., invalid UUID format) */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex) {
    String errorMsg =
        String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
    log.warn("Type mismatch: {}", errorMsg);
    return ResponseEntity.badRequest().body(ApiResponse.error(errorMsg));
  }

  /** Handle 400 Bad Request - Invalid business logic arguments */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
    log.warn("Bad request: {}", ex.getMessage());
    return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * Handle upstream REST client errors Note: This usually won't be hit because EmployeeClient
   * handles its own errors, but it's here as a safety net.
   */
  @ExceptionHandler(RestClientResponseException.class)
  public ResponseEntity<ApiResponse<Void>> handleRestClientError(RestClientResponseException ex) {
    log.error("Upstream REST error [{}]: {}", ex.getRawStatusCode(), ex.getMessage());

    // Map upstream status to appropriate response
    HttpStatus status = HttpStatus.valueOf(ex.getRawStatusCode());
    if (status.is5xxServerError()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(ApiResponse.error("Upstream service unavailable"));
    }

    return ResponseEntity.status(status)
        .body(ApiResponse.error("Upstream error: " + ex.getStatusText()));
  }

  @ExceptionHandler({
    io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
    java.util.concurrent.TimeoutException.class,
    java.net.ConnectException.class
  })
  public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(Exception ex) {
    log.error("Service unavailable: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error("Service temporarily unavailable"));
  }

  /** Handle all other unexpected exceptions - 500 Internal Server Error */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
    log.error("Unexpected error: {}", ex.getMessage(), ex);

    // Obfuscate internal error details to the caller
    String message = "An unexpected error occurred";

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(message));
  }
}
