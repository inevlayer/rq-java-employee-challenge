package com.reliaquest.api.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliaquest.api.client.EmployeeClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Configuration
public class EmployeeBeansConfig {

  private static final Logger log = LoggerFactory.getLogger(EmployeeBeansConfig.class);

  @Bean
  public RestClient restClient(@Value("${api.base-url:http://localhost:8112}") String baseUrl) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.findAndRegisterModules();
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);

    return RestClient.builder()
        .baseUrl(baseUrl)
        .messageConverters(
            list -> {
              list.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
              list.add(converter);
            })
        .defaultHeader("Accept", "application/json")
        .defaultHeader("Content-Type", "application/json")
        .requestInterceptor(
            (request, body, execution) -> {
              log.debug("Request: {} {}", request.getMethod(), request.getURI());
              try {
                var response = execution.execute(request, body);
                log.debug("Response: {} {}", response.getStatusCode(), response.getHeaders());
                return response;
              } catch (Exception e) {
                log.error(
                    " Error executing request [{} {}]: {}",
                    request.getMethod(),
                    request.getURI(),
                    e.getMessage());
                throw e;
              }
            })
        .build();
  }

  @Bean
  public EmployeeClient employeeClient(
      RestClient restClient,
      CircuitBreakerRegistry cbRegistry,
      RetryRegistry retryRegistry,
      RateLimiterRegistry rlRegistry) {
    return new EmployeeClient(restClient, cbRegistry, retryRegistry, rlRegistry);
  }

  @Bean
  public GroupedOpenApi publicApi() {
    return GroupedOpenApi.builder().group("public").pathsToMatch("/**").build();
  }

  @Bean
  public CircuitBreakerRegistry circuitBreakerRegistry() {
    CircuitBreakerConfig cbConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(50)
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(2)
            .minimumNumberOfCalls(5)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .recordExceptions(RestClientResponseException.class, ResourceAccessException.class)
            .ignoreExceptions(EmployeeClient.NonRetryableHttpException.class)
            .build();

    return CircuitBreakerRegistry.of(cbConfig);
  }

  @Bean
  public RetryRegistry retryRegistry() {
    RetryConfig retryConfig =
        RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofSeconds(1))
            .retryExceptions(RestClientResponseException.class, ResourceAccessException.class)
            .ignoreExceptions(EmployeeClient.NonRetryableHttpException.class)
            .failAfterMaxAttempts(true)
            .build();

    return RetryRegistry.of(retryConfig);
  }

  @Bean
  public RateLimiterRegistry rateLimiterRegistry() {
    RateLimiterConfig rlConfig =
        RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .limitForPeriod(3) //  3 calls per second
            .timeoutDuration(Duration.ofMillis(500))
            .build();

    return RateLimiterRegistry.of(rlConfig);
  }
}
