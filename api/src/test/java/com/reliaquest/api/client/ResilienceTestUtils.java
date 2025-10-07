package com.reliaquest.api.client;

import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.ratelimiter.*;
import io.github.resilience4j.retry.*;
import java.time.Duration;
import java.util.Set;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

final class ResilienceTestUtils {

  private ResilienceTestUtils() {}

  static final String CB_NAME = "employeeClientCircuit";
  static final String RL_NAME = "employeeClientLimiter";
  static final String RETRY_NAME = "employeeClientRetry";

  static void resetResilience(
      CircuitBreakerRegistry cbRegistry,
      RateLimiterRegistry rlRegistry,
      RetryRegistry retryRegistry) {

    cbRegistry.remove(CB_NAME);
    CircuitBreakerConfig cbConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(5)
            .minimumNumberOfCalls(3)
            .failureRateThreshold(60f)
            .waitDurationInOpenState(Duration.ofMillis(300))
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(RestClientResponseException.class, ResourceAccessException.class)
            .ignoreExceptions(EmployeeClient.NonRetryableHttpException.class)
            .build();

    cbRegistry.circuitBreaker(CB_NAME, cbConfig);

    rlRegistry.remove(RL_NAME);
    RateLimiterConfig rlConfig =
        RateLimiterConfig.custom()
            .limitForPeriod(100)
            .limitRefreshPeriod(Duration.ofMillis(100))
            .timeoutDuration(Duration.ZERO)
            .build();

    rlRegistry.rateLimiter(RL_NAME, rlConfig);

    retryRegistry.remove(RETRY_NAME);
    RetryConfig retryConfig =
        RetryConfig.<Object>custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(50))
            .retryOnException(
                ex -> {
                  if (ex instanceof ResourceAccessException) return true;
                  if (ex instanceof RestClientResponseException rex) {
                    int code = rex.getRawStatusCode();
                    // Only retry  specific transient codes
                    return Set.of(429, 502, 503, 504).contains(code);
                  }
                  return false;
                })
            .ignoreExceptions(EmployeeClient.NonRetryableHttpException.class)
            .failAfterMaxAttempts(false) // Return result instead of throwing
            .build();

    retryRegistry.retry(RETRY_NAME, retryConfig);
  }

  /** Reset with custom circuit breaker config for specific test scenarios */
  static void resetResilienceWithCustomCB(
      CircuitBreakerRegistry cbRegistry,
      RateLimiterRegistry rlRegistry,
      RetryRegistry retryRegistry,
      int slidingWindowSize,
      int minimumCalls,
      float failureThreshold) {
    cbRegistry.remove(CB_NAME);
    CircuitBreakerConfig cbConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(slidingWindowSize)
            .minimumNumberOfCalls(minimumCalls)
            .failureRateThreshold(failureThreshold)
            .waitDurationInOpenState(Duration.ofMillis(200))
            .permittedNumberOfCallsInHalfOpenState(1)
            .recordExceptions(RestClientResponseException.class, ResourceAccessException.class)
            .ignoreExceptions(EmployeeClient.NonRetryableHttpException.class)
            .build();

    cbRegistry.circuitBreaker(CB_NAME, cbConfig);

    // Reset rate limiter and retry with defaults
    rlRegistry.remove(RL_NAME);
    rlRegistry.rateLimiter(
        RL_NAME,
        RateLimiterConfig.custom()
            .limitForPeriod(100)
            .limitRefreshPeriod(Duration.ofMillis(100))
            .timeoutDuration(Duration.ZERO)
            .build());

    retryRegistry.remove(RETRY_NAME);
    retryRegistry.retry(
        RETRY_NAME,
        RetryConfig.<Object>custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(50))
            .retryOnException(
                ex -> {
                  if (ex instanceof ResourceAccessException) return true;
                  if (ex instanceof RestClientResponseException rex) {
                    return Set.of(429, 502, 503, 504).contains(rex.getRawStatusCode());
                  }
                  return false;
                })
            .failAfterMaxAttempts(false)
            .build());
  }
}
