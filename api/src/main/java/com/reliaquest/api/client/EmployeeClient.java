package com.reliaquest.api.client;

import com.reliaquest.api.model.ApiResponse;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.DeleteEmployeeInput;
import com.reliaquest.api.model.Employee;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.*;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
public class EmployeeClient {

  private final RestClient restClient;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;
  private final RateLimiter rateLimiter;

  private static final String BASE_PATH = "/api/v1/employee";
  private static final Set<Integer> TRANSIENT_CODES = Set.of(429, 502, 503, 504);

  public EmployeeClient(
      RestClient restClient,
      CircuitBreakerRegistry cbRegistry,
      RetryRegistry retryRegistry,
      RateLimiterRegistry rlRegistry) {
    this.restClient = restClient;
    this.circuitBreaker = cbRegistry.circuitBreaker("employeeClientCircuit");
    this.retry = retryRegistry.retry("employeeClientRetry");
    this.rateLimiter = rlRegistry.rateLimiter("employeeClientLimiter");
  }

  private <T> Supplier<T> decorateResilience(Supplier<T> supplier) {
    Supplier<T> rateLimited = RateLimiter.decorateSupplier(rateLimiter, supplier);
    Supplier<T> circuitProtected = CircuitBreaker.decorateSupplier(circuitBreaker, rateLimited);
    return Retry.decorateSupplier(retry, circuitProtected);
  }

  public List<Employee> getAllEmployees() {
    Supplier<List<Employee>> supplier =
        () -> {
          try {
            ApiResponse<List<Employee>> response =
                restClient
                    .get()
                    .uri(BASE_PATH)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null || response.data() == null) {
              log.warn("Received empty employee list from server");
              return Collections.emptyList();
            }
            return response.data();
          } catch (RestClientResponseException e) {
            return handleRestErrorList(e, "getAllEmployees");
          } catch (Exception e) {
            log.error("Unhandled error fetching employees: {}", e.toString());
            return Collections.emptyList();
          }
        };
    return decorateResilience(supplier).get();
  }

  public Optional<Employee> getEmployeeById(String id) {
    if (id == null || id.isBlank()) {
      log.warn("Cannot get employee: id is null or blank");
      return Optional.empty();
    }

    Supplier<Optional<Employee>> supplier =
        () -> {
          try {
            ApiResponse<Employee> response =
                restClient
                    .get()
                    .uri(BASE_PATH + "/{id}", id)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null || response.data() == null) {
              log.warn("Employee not found for ID {}", id);
              return Optional.empty();
            }
            return Optional.of(response.data());
          } catch (RestClientResponseException e) {
            return handleRestErrorOptional(e, "getEmployeeById", id);
          } catch (Exception e) {
            log.error("Unhandled error fetching employee {}: {}", id, e.toString());
            return Optional.empty();
          }
        };
    return decorateResilience(supplier).get();
  }

  public Optional<Employee> createEmployee(CreateEmployeeInput input) {
    if (input == null) {
      log.warn("Cannot create employee: input is null");
      return Optional.empty();
    }

    Supplier<Optional<Employee>> supplier =
        () -> {
          try {
            ApiResponse<Employee> response =
                restClient
                    .post()
                    .uri(BASE_PATH)
                    .body(input)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null || response.data() == null) {
              log.warn("Failed to create employee (empty response)");
              return Optional.empty();
            }
            return Optional.of(response.data());
          } catch (RestClientResponseException e) {
            return handleRestErrorOptional(e, "createEmployee", input.getName());
          } catch (Exception e) {
            log.error("Unhandled error creating employee: {}", e.toString());
            return Optional.empty();
          }
        };
    return decorateResilience(supplier).get();
  }

  public Optional<Employee> updateEmployee(String id, CreateEmployeeInput input) {
    if (id == null || id.isBlank() || input == null) {
      log.warn("Cannot update employee: id or input is invalid");
      return Optional.empty();
    }

    Supplier<Optional<Employee>> supplier =
        () -> {
          try {
            ApiResponse<Employee> response =
                restClient
                    .put()
                    .uri(BASE_PATH + "/{id}", id)
                    .body(input)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null || response.data() == null) {
              log.warn("Failed to update employee {} (empty response)", id);
              return Optional.empty();
            }
            return Optional.of(response.data());
          } catch (RestClientResponseException e) {
            return handleRestErrorOptional(e, "updateEmployee", id);
          } catch (Exception e) {
            log.error("Unhandled error updating employee {}: {}", id, e.toString());
            return Optional.empty();
          }
        };
    return decorateResilience(supplier).get();
  }

  public boolean deleteEmployee(String id) {
    if (id == null || id.isBlank()) {
      log.warn("Cannot delete employee: id is null or blank");
      return false;
    }

    Supplier<Boolean> supplier =
        () -> {
          try {
            restClient.delete().uri(BASE_PATH + "/{id}", id).retrieve().toBodilessEntity();
            return true;
          } catch (RestClientResponseException e) {
            return handleRestErrorDelete(e, id);
          } catch (Exception e) {
            log.error("Unhandled error deleting employee {}: {}", id, e.toString());
            return false;
          }
        };
    return decorateResilience(supplier).get();
  }

  public boolean deleteEmployeeByName(String name) {
    if (name == null || name.isBlank()) {
      log.warn("Cannot delete employee: name is null or blank");
      return false;
    }

    Supplier<Boolean> supplier =
        () -> {
          try {
            ApiResponse<Boolean> response =
                restClient
                    .method(HttpMethod.DELETE)
                    .uri(BASE_PATH)
                    .body(new DeleteEmployeeInput(name))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return Optional.ofNullable(response).map(ApiResponse::data).orElse(false);
          } catch (RestClientResponseException e) {
            return handleRestErrorDeleteByName(e, name);
          } catch (Exception e) {
            log.error("Unhandled error deleting employee by name {}: {}", name, e.toString());
            return false;
          }
        };
    return decorateResilience(supplier).get();
  }

  // Error Handling
  private boolean handleRestErrorDelete(RestClientResponseException e, String id) {
    HttpStatus status = HttpStatus.resolve(e.getRawStatusCode());
    if (status == HttpStatus.NOT_FOUND) {
      log.warn("Employee {} not found for deletion (404)", id);
      return false;
    }
    if (isTransientError(status)) {
      log.warn("Transient delete error for {} ({}), will retry", id, status);
      throw e;
    }
    log.error("Delete error for {}: {} – {}", id, e.getRawStatusCode(), e.getMessage());
    return false;
  }

  private boolean handleRestErrorDeleteByName(RestClientResponseException e, String name) {
    HttpStatus status = HttpStatus.resolve(e.getRawStatusCode());
    if (status == HttpStatus.NOT_FOUND) {
      log.warn("Employee {} not found for deletion (404)", name);
      return false;
    }
    if (isTransientError(status)) {
      log.warn("Transient delete error for {} ({}), will retry", name, status);
      throw e;
    }
    log.error("Delete-by-name error for {}: {} – {}", name, e.getRawStatusCode(), e.getMessage());
    return false;
  }

  private Optional<Employee> handleRestErrorOptional(RestClientResponseException e, String method) {
    HttpStatus status = HttpStatus.resolve(e.getRawStatusCode());
    if (status == HttpStatus.NOT_FOUND) {
      log.warn("{}: Resource not found (404)", method);
      return Optional.empty();
    }
    if (status == HttpStatus.TOO_MANY_REQUESTS || status == HttpStatus.SERVICE_UNAVAILABLE) {
      log.warn("{}: Transient error ({}), will trigger retry", method, status);
      throw e;
    }
    log.error("Non-retryable error in {}: {} – {}", method, e.getRawStatusCode(), e.getMessage());
    return Optional.empty();
  }

  private List<Employee> handleRestErrorList(RestClientResponseException e, String method) {
    HttpStatus status = HttpStatus.resolve(e.getRawStatusCode());
    if (isTransientError(status)) {
      log.warn("Transient error in {}: {} – triggering retry", method, status);
      throw e; // Let retry mechanism handle it
    }
    log.error("Non-retryable error in {}: {} – {}", method, e.getRawStatusCode(), e.getMessage());
    return Collections.emptyList();
  }

  private Optional<Employee> handleRestErrorOptional(
      RestClientResponseException e, String method, String identifier) {
    HttpStatus status = HttpStatus.resolve(e.getRawStatusCode());
    if (status == HttpStatus.NOT_FOUND) {
      log.warn("{}: Resource not found (404) for {}", method, identifier);
      return Optional.empty();
    }
    if (isTransientError(status)) {
      log.warn("{}: Transient error ({}), will trigger retry for {}", method, status, identifier);
      throw e; // Let retry mechanism handle it
    }
    if (status != null && status.is4xxClientError()) {
      log.warn(
          "{}: Client error {} for {}: {}", method, status.value(), identifier, e.getMessage());
      return Optional.empty();
    }
    log.error("Non-retryable error in {}: {} – {}", method, e.getRawStatusCode(), e.getMessage());
    return Optional.empty();
  }

  private boolean isTransientError(HttpStatus status) {
    if (status == null) return false;
    return status == HttpStatus.TOO_MANY_REQUESTS
        || status == HttpStatus.BAD_GATEWAY
        || status == HttpStatus.SERVICE_UNAVAILABLE
        || status == HttpStatus.GATEWAY_TIMEOUT;
  }

  public static final class NonRetryableHttpException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public NonRetryableHttpException(int statusCode, String responseBody, Throwable cause) {
      super("Non-retryable HTTP error " + statusCode + ": " + responseBody, cause);
      this.statusCode = statusCode;
      this.responseBody = responseBody;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public String getResponseBody() {
      return responseBody;
    }
  }
}
