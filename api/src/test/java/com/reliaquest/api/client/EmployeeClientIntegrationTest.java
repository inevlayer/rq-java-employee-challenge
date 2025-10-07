package com.reliaquest.api.client;

import static org.junit.jupiter.api.Assertions.*;

import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = "api.base-url=http://localhost:${mock.server.port}")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class EmployeeClientIntegrationTest {

  private static MockWebServer mockServer;

  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;

  @Autowired private RateLimiterRegistry rateLimiterRegistry;

  @Autowired private RetryRegistry retryRegistry;

  @Autowired private EmployeeClient employeeClient;

  @DynamicPropertySource
  static void registerBaseUrl(DynamicPropertyRegistry registry) {
    try {
      mockServer = new MockWebServer();
      mockServer.start();
      registry.add("mock.server.port", () -> mockServer.getPort());
      registry.add("api.base-url", () -> mockServer.url("/").toString());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @AfterEach
  void cleanup() {
    try {
      while (true) {
        RecordedRequest request = mockServer.takeRequest(10, TimeUnit.MILLISECONDS);
        if (request == null) break;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @AfterAll
  void teardown() throws IOException {
    mockServer.shutdown();
  }

  @Test
  void contextLoads() {
    assertNotNull(employeeClient);
  }

  @Test
  void getAllEmployees_returnsList() throws Exception {
    String jsonBody =
        """
            {
              "data": [
                {
                  "id": "33333333-3333-3333-3333-333333333333",
                  "employee_name": "Alice",
                  "employee_salary": 9000,
                  "employee_age": 35,
                  "employee_title": "Architect",
                  "employee_email": "alice@company.com"
                }
              ],
              "status": "success"
            }
            """;

    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(jsonBody)
            .addHeader("Content-Type", "application/json"));

    List<Employee> employees = employeeClient.getAllEmployees();

    assertNotNull(employees);
    assertEquals(1, employees.size());
    assertEquals("Alice", employees.get(0).getEmployeeName());
    assertEquals(9000, employees.get(0).getEmployeeSalary());

    // Verify the request was made
    RecordedRequest request = mockServer.takeRequest(1, TimeUnit.SECONDS);
    assertNotNull(request);
    assertEquals("GET", request.getMethod());
    assertTrue(Objects.requireNonNull(request.getPath()).contains("/api/v1/employee"));
  }

  @Test
  void getAllEmployees_handles429Gracefully() {
    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(429)
            .setBody("{\"status\":\"error\",\"error\":\"Too many requests\"}")
            .addHeader("Content-Type", "application/json"));

    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody("""
                { "data": [], "status": "success" }
                """)
            .addHeader("Content-Type", "application/json"));

    List<Employee> employees = employeeClient.getAllEmployees();

    assertNotNull(employees);
    assertTrue(employees.isEmpty());
  }

  @Test
  void getEmployeeById_returnsEmployee() {
    String jsonBody =
        """
            {
              "status": "success",
              "data": {
                "id": "11111111-1111-1111-1111-111111111111",
                "employee_name": "John",
                "employee_salary": 8000,
                "employee_age": 30,
                "employee_title": "Engineer",
                "employee_email": "john@company.com"
              },
              "error": null
            }
            """;

    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(jsonBody)
            .addHeader("Content-Type", "application/json"));

    Optional<Employee> emp = employeeClient.getEmployeeById("11111111-1111-1111-1111-111111111111");

    assertTrue(emp.isPresent(), "Employee should be present");
    assertEquals("John", emp.get().getEmployeeName());
    assertEquals(8000, emp.get().getEmployeeSalary());
    assertEquals(30, emp.get().getEmployeeAge());
  }

  @Test
  void getEmployeeById_handles404Gracefully() {
    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(404)
            .setBody(
                """
                    {"status":"error","data":null,"error":"Not found"}
                    """)
            .addHeader("Content-Type", "application/json"));

    Optional<Employee> emp = employeeClient.getEmployeeById("nonexistent-id");

    assertTrue(emp.isEmpty(), "Should return empty Optional for 404");
  }

  @Test
  void getEmployeeById_retriesOn503() {
    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(503)
            .setBody("{\"status\":\"error\"}")
            .addHeader("Content-Type", "application/json"));

    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(503)
            .setBody("{\"status\":\"error\"}")
            .addHeader("Content-Type", "application/json"));
    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                    {
                      "status":"success",
                      "data":{
                        "id":"11111111-1111-1111-1111-111111111111",
                        "employee_name":"Recovered",
                        "employee_salary":5000,
                        "employee_age":28,
                        "employee_title":"Dev",
                        "employee_email":"recovered@company.com"
                      }
                    }
                    """)
            .addHeader("Content-Type", "application/json"));

    Optional<Employee> emp = employeeClient.getEmployeeById("11111111-1111-1111-1111-111111111111");

    assertTrue(emp.isPresent(), "Employee should be recovered after retries");
    assertEquals("Recovered", emp.get().getEmployeeName());

    assertTrue(
        mockServer.getRequestCount() >= 2, "Should have retried at least once after 503 errors");
  }

  @Test
  void createEmployee_createsSuccessfully() throws Exception {
    String jsonResponse =
        """
            {
              "status":"success",
              "data":{
                "id":"11111111-1111-1111-1111-111111111111",
                "employee_name":"Bob",
                "employee_salary":7000,
                "employee_age":25,
                "employee_title":"Intern",
                "employee_email":"bob@company.com"
              },
              "error":null
            }
            """;

    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(201)
            .setBody(jsonResponse)
            .addHeader("Content-Type", "application/json"));

    CreateEmployeeInput input = new CreateEmployeeInput();
    input.setName("Bob");
    input.setSalary(7000);
    input.setAge(25);
    input.setTitle("Intern");

    Optional<Employee> created = employeeClient.createEmployee(input);

    assertTrue(created.isPresent(), "Created employee should be present");
    assertEquals("Bob", created.get().getEmployeeName());
    assertEquals(7000, created.get().getEmployeeSalary());
    assertEquals(25, created.get().getEmployeeAge());

    // Verify POST request was made
    RecordedRequest request = mockServer.takeRequest(1, TimeUnit.SECONDS);
    assertNotNull(request);
    assertEquals("POST", request.getMethod());
  }

  @Test
  void createEmployee_handlesNullInput() {
    Optional<Employee> result = employeeClient.createEmployee(null);

    assertTrue(result.isEmpty(), "Should return empty Optional for null input");
  }

  @Test
  void deleteEmployeeByName_deletesSuccessfully() {
    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                    {"status":"success","data":true}
                    """)
            .addHeader("Content-Type", "application/json"));

    boolean deleted = employeeClient.deleteEmployeeByName("John Doe");

    assertTrue(deleted, "Delete should return true on success");
  }

  @Test
  void deleteEmployeeByName_handles404() {
    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(404)
            .setBody(
                """
                    {"status":"error","data":null,"error":"Not found"}
                    """)
            .addHeader("Content-Type", "application/json"));

    boolean deleted = employeeClient.deleteEmployeeByName("NonExistent");

    assertFalse(deleted, "Delete should return false for 404");
  }

  @Test
  void getAllEmployees_handlesEmptyResponse() {
    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody("""
                    {"data":[],"status":"success"}
                    """)
            .addHeader("Content-Type", "application/json"));

    List<Employee> employees = employeeClient.getAllEmployees();

    assertNotNull(employees);
    assertTrue(employees.isEmpty(), "Should return empty list for no employees");
  }

  @Test
  void getAllEmployees_handlesNullData() {
    mockServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                    {"data":null,"status":"success"}
                    """)
            .addHeader("Content-Type", "application/json"));

    List<Employee> employees = employeeClient.getAllEmployees();

    assertNotNull(employees);
    assertTrue(employees.isEmpty(), "Should return empty list for null data");
  }
}
