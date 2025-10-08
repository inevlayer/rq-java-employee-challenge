package com.reliaquest.api.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.reliaquest.api.model.ApiResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handleNotFound_shouldReturn404() {
    NoSuchElementException ex = new NoSuchElementException("Employee not found");

    ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo("error");
    assertThat(response.getBody().error()).contains("Employee not found");
  }

  @Test
  void handleValidation_shouldReturn400WithFieldErrors() {
    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);

    FieldError fieldError1 = new FieldError("employee", "name", "must not be blank");
    FieldError fieldError2 = new FieldError("employee", "age", "must be at least 16");

    when(ex.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

    ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo("error");
    assertThat(response.getBody().error()).contains("name: must not be blank");
    assertThat(response.getBody().error()).contains("age: must be at least 16");
  }

  @Test
  void handleValidation_shouldReturnDefaultMessage_whenNoFieldErrors() {
    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);

    when(ex.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getFieldErrors()).thenReturn(List.of());

    ResponseEntity<ApiResponse<Void>> response = handler.handleValidation(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error()).isEqualTo("Validation failed");
  }

  @Test
  void handleTypeMismatch_shouldReturn400() {
    MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
    when(ex.getName()).thenReturn("id");
    when(ex.getValue()).thenReturn("invalid-uuid");

    ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error())
        .contains("Invalid value 'invalid-uuid' for parameter 'id'");
  }

  @Test
  void handleBadRequest_shouldReturn400() {
    IllegalArgumentException ex = new IllegalArgumentException("Invalid salary value");

    ResponseEntity<ApiResponse<Void>> response = handler.handleBadRequest(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error()).isEqualTo("Invalid salary value");
  }

  @Test
  void handleRestClientError_shouldReturn503_for5xxErrors() {
    RestClientResponseException ex =
        new RestClientResponseException(
            "Service error",
            503,
            "Service Unavailable",
            null,
            "error".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);

    ResponseEntity<ApiResponse<Void>> response = handler.handleRestClientError(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error()).isEqualTo("Upstream service unavailable");
  }

  @Test
  void handleRestClientError_shouldReturnOriginalStatus_for4xxErrors() {
    RestClientResponseException ex =
        new RestClientResponseException(
            "Not found",
            404,
            "Not Found",
            null,
            "error".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);

    ResponseEntity<ApiResponse<Void>> response = handler.handleRestClientError(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error()).contains("Upstream error");
  }

  @Test
  void handleServiceUnavailable_circuitBreakerOpen_shouldReturn503() {
    CircuitBreakerConfig config = CircuitBreakerConfig.ofDefaults();
    CircuitBreaker circuitBreaker = CircuitBreaker.of("testCircuit", config);

    CallNotPermittedException ex =
        CallNotPermittedException.createCallNotPermittedException(circuitBreaker);

    ResponseEntity<ApiResponse<Void>> response = handler.handleServiceUnavailable(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error()).isEqualTo("Service temporarily unavailable");
  }

  @Test
  void handleServiceUnavailable_timeout_shouldReturn503() {
    TimeoutException ex = new TimeoutException("Request timeout");

    ResponseEntity<ApiResponse<Void>> response = handler.handleServiceUnavailable(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error()).isEqualTo("Service temporarily unavailable");
  }

  @Test
  void handleServiceUnavailable_connectionError_shouldReturn503() {
    ConnectException ex = new ConnectException("Connection refused");

    ResponseEntity<ApiResponse<Void>> response = handler.handleServiceUnavailable(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error()).isEqualTo("Service temporarily unavailable");
  }

  @Test
  void handleGeneric_shouldReturn500() {
    RuntimeException ex = new RuntimeException("Unexpected database error");

    ResponseEntity<ApiResponse<Void>> response = handler.handleGeneric(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo("error");
    assertThat(response.getBody().error()).isEqualTo("An unexpected error occurred");
  }

  @Test
  void handleGeneric_shouldObfuscateErrorDetails() {
    RuntimeException ex = new RuntimeException("Database password: secret123");

    ResponseEntity<ApiResponse<Void>> response = handler.handleGeneric(ex);

    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error()).doesNotContain("password");
    assertThat(response.getBody().error()).doesNotContain("secret123");
    assertThat(response.getBody().error()).isEqualTo("An unexpected error occurred");
  }
}
