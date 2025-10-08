# Design Decisions

## Architecture & Technology Choices

### REST Client
This implementation uses Spring's **RestClient** (introduced in Spring 6.1) instead of the deprecated `RestTemplate`. This choice ensures:
- Modern, fluent API for HTTP communications
- Better integration with Resilience4j decorators
- Future-ready for reactive patterns if needed
- Simplified request/response handling with type-safe builders

### Resilience Patterns
The client implements three complementary resilience patterns via Resilience4j:
1. **Circuit Breaker** - Prevents cascading failures by opening after 50% failure rate
2. **Retry** - Automatically retries transient errors (429, 503, 504) with exponential backoff
3. **Rate Limiter** - Controls outbound request rate to respect upstream API limits

These are composed in order: Rate Limiter → Circuit Breaker → Retry, ensuring proper backpressure and fault isolation.

### Caching Strategy
A **simple in-memory cache** with 2-minute TTL is implemented for demonstration purposes. The cache:
- Invalidates on successful CREATE and DELETE operations
- Uses `Clock` abstraction for testability
- Is thread-safe via `synchronized` methods

**Production Considerations:**
- Replace with **Redis** for distributed caching across service instances
- Or use **Caffeine** for high-performance local caching with automatic eviction
- Add cache warming strategies for frequently accessed data
- Implement proper cache-aside pattern with fallback logic

### Error Handling
Multi-layered error handling approach:
1. **Client Layer** - Returns `Optional` and empty collections, never throws to service layer
2. **Service Layer** - Validates inputs, handles business logic
3. **Controller Layer** - Maps domain results to HTTP responses
4. **Global Exception Handler** - Provides consistent error responses, obfuscates sensitive details

This design prevents internal errors from leaking to clients while maintaining debuggability through logging.

### Delete Operation Design
The `DELETE /{id}` endpoint accepts both UUID and name parameters to match the interface contract, but the mock server API only supports delete-by-name. The solution:
1. Check if parameter is a valid UUID
2. If UUID: fetch employee → extract name → delete by name
3. If not UUID: treat as name → delete directly


## Testing Approach

### Unit Tests
- **Service Layer**: Uses `MutableClock` for deterministic cache expiry testing
- **Controller Layer**: `@WebMvcTest` with mocked services for focused endpoint testing
- **Exception Handler**: Comprehensive coverage of all error scenarios

### Integration Tests
- **MockWebServer**: Provides deterministic client-level verification without external dependencies
- **Resilience4j**: Tests retry, circuit breaker, and rate limiting behaviors
- **Spring Boot Test**: Full context loading validates bean wiring and configuration

## Known Limitations & Future Enhancements

### Not Implemented (Time Constraints)
1. **Observability**
    - Micrometer metrics for request counts, latencies, cache hit rates
    - OpenTelemetry for distributed tracing
    - Zipkin integration for request flow visualization
    - Structured logging with correlation IDs

2. **Advanced Resilience**
    - Bulkhead pattern for resource isolation
    - Time limiter for request timeouts
    - Fallback mechanisms with degraded functionality

3. **Security**
    - Authentication/authorization headers
    - TLS/SSL configuration
    - API key management
    - Request signing

4. **Production Readiness**
    - Health checks and readiness probes
    - Graceful shutdown handling
    - Connection pooling configuration
    - Comprehensive integration tests against real mock server

### Potential Improvements
1. **Request Interceptor**: Add custom `RestClient` request interceptor for:
    - Centralized request/response logging
    - Request/response time tracking
    - Correlation ID injection
    - Detailed diagnostics for debugging
    - Better than MockWebServer counters for production monitoring

2. **Async Operations**: Consider `CompletableFuture` for non-blocking operations in high-throughput scenarios

3. **Batch Operations**: Add bulk create/delete endpoints to reduce network overhead

4. **Pagination**: Support paginated queries for large employee datasets

5. **Field Filtering**: Allow clients to specify which fields to return

## Design Trade-offs

### Simplicity vs Performance
- **Choice**: In-memory caching with simple invalidation
- **Trade-off**: Limits scalability but reduces complexity
- **Rationale**: Appropriate for demonstration; production would require distributed cache

### Consistency vs Availability
- **Choice**: Cache invalidation on writes
- **Trade-off**: Potential stale reads between invalidation and next fetch
- **Rationale**: Acceptable for non-critical employee data; eventual consistency model

### Error Handling Philosophy
- **Choice**: Return empty collections/optionals rather than throwing exceptions
- **Trade-off**: Callers must check for empty results
- **Rationale**: Makes error handling explicit, prevents exception-driven control flow

## Configuration Notes

### Resilience4j Tuning
Current settings are conservative for demonstration:
- Circuit breaker: 10-call sliding window, 50% failure threshold
- Retry: 3 attempts with 1-2 second backoff
- Rate limiter: 3 calls/second

**Production tuning** should be based on:
- Upstream API SLAs and rate limits
- Expected traffic patterns
- Acceptable latency percentiles
- Failure rate observations

### Cache TTL
2-minute TTL balances freshness with API load. Adjust based on:
- Employee data change frequency
- API cost/quota considerations
- Acceptable staleness for your use case

## Dependency Versions

- **Spring Boot**: 3.x
- **Java**: 17
- **Resilience4j**: 2.2.0
- **JUnit**: 5.10.2
- **MockWebServer**: 4.12.0

## Package Structure