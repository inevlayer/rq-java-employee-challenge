package com.reliaquest.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.reliaquest.api.client.EmployeeClient;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {
  @Mock EmployeeClient employeeClient;

  EmployeeService employeeService;

  private MutableClock clock;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.now(), ZoneId.systemDefault());
    employeeService = new EmployeeService(employeeClient, clock);
  }

  @Test
  void getAllEmployeesCached() {
    List<Employee> employees =
        List.of(
            new Employee(UUID.randomUUID(), "Ada", 3000, 30, "EA", "ada@company.com"),
            new Employee(UUID.randomUUID(), "Bob", 3200, 31, "Sales Engineer", "bob@company.com"));
    when(employeeClient.getAllEmployees()).thenReturn(employees);

    List<Employee> result = employeeService.getAllEmployees();

    assertEquals(employees.size(), result.size());
    assertEquals("Ada", result.get(0).getEmployeeName());
    assertEquals("Bob", result.get(1).getEmployeeName());
  }

  @Test
  void getAllEmployeesCached_fetchesOnceUntilExpired() {
    var employees =
        List.of(new Employee(UUID.randomUUID(), "John", 1000, 30, "Dev", "john@company.com"));
    when(employeeClient.getAllEmployees()).thenReturn(employees);

    // First call hits client
    var first = employeeService.getAllEmployees();
    // Second call should use cache
    var second = employeeService.getAllEmployees();

    verify(employeeClient, times(1)).getAllEmployees();

    assertThat(first).isEqualTo(second);
    assertThat(first).containsExactlyElementsOf(second);
  }

  @Test
  void createEmployee_invalidatesCache() {
    var employees =
        List.of(
            new Employee(UUID.randomUUID(), "Existing", 1000, 30, "Dev", "existing@company.com"));
    when(employeeClient.getAllEmployees()).thenReturn(employees);

    var input = new CreateEmployeeInput();
    input.setName("Jamie");
    input.setSalary(2000);
    input.setAge(35);
    input.setTitle("Mgr");

    var newEmployee =
        new Employee(UUID.randomUUID(), "Jamie", 2000, 35, "Mgr", "jamie@company.com");
    when(employeeClient.createEmployee(any())).thenReturn(Optional.of(newEmployee));

    // Prime the cache
    employeeService.getAllEmployeesCached();
    verify(employeeClient, times(1)).getAllEmployees();

    // Create employee - should invalidate cache
    employeeService.createEmployee(input);
    verify(employeeClient, times(1)).createEmployee(any());

    // Next call should refetch due to invalidation
    employeeService.getAllEmployees();
    verify(employeeClient, times(2)).getAllEmployees();
  }

  @Test
  void deleteEmployeeByName_invalidatesCacheOnSuccess() {
    var employees =
        List.of(new Employee(UUID.randomUUID(), "John", 1000, 30, "Dev", "john@company.com"));
    when(employeeClient.getAllEmployees()).thenReturn(employees);
    when(employeeClient.deleteEmployeeByName("John")).thenReturn(true);

    employeeService.getAllEmployeesCached();
    verify(employeeClient, times(1)).getAllEmployees();

    employeeService.deleteEmployeeByName("John");

    // Should trigger re-fetch
    employeeService.getAllEmployees();
    verify(employeeClient, times(2)).getAllEmployees();
  }

  @Test
  void deleteEmployeeByName_doesNotInvalidateCacheOnFailure() {
    var employees =
        List.of(new Employee(UUID.randomUUID(), "John", 1000, 30, "Dev", "john@company.com"));
    when(employeeClient.getAllEmployees()).thenReturn(employees);
    when(employeeClient.deleteEmployeeByName("NonExistent")).thenReturn(false);

    // Prime cache
    employeeService.getAllEmployeesCached();
    verify(employeeClient, times(1)).getAllEmployees();

    // Try to delete non-existent employee
    Optional<String> result = employeeService.deleteEmployeeByName("NonExistent");
    assertThat(result).isEmpty();

    // Should still use cache (not invalidated)
    employeeService.getAllEmployees();
    verify(employeeClient, times(1)).getAllEmployees();
  }

  @Test
  void testDeleteEmployeeByIdOrName_nullInput() {
    Optional<String> result = employeeService.deleteEmployeeByIdOrName(null);
    assertTrue(result.isEmpty());
  }

  @Test
  void testGetTopTenHighestEarningNames_basic() {
    List<Employee> mockList =
        IntStream.range(1, 20)
            .mapToObj(
                i ->
                    new Employee(
                        UUID.randomUUID(),
                        "employee:" + i,
                        i * 1000,
                        20 + i,
                        "title: ",
                        "email@company.com"))
            .collect(Collectors.toList());

    when(employeeClient.getAllEmployees()).thenReturn(mockList);

    List<String> topTen = employeeService.getTopTenHighestEarningNames();
    assertEquals(10, topTen.size());
    assertEquals("employee:19", topTen.get(0));
  }

  @Test
  void testDeleteEmployeeByIdOrName_uuidFound() {
    UUID uuid = UUID.randomUUID();
    Employee mockEmployee = new Employee(uuid, "John Doe", 1000, 30, "Dev", "jdoe@company.com");

    when(employeeClient.getEmployeeById(uuid.toString())).thenReturn(Optional.of(mockEmployee));
    when(employeeClient.deleteEmployeeByName("John Doe")).thenReturn(true);

    Optional<String> result = employeeService.deleteEmployeeByIdOrName(uuid.toString());

    assertTrue(result.isPresent());
    assertEquals("John Doe", result.get());
  }

  @Test
  void testDeleteEmployeeByIdOrName_uuidNotFound() {
    UUID uuid = UUID.randomUUID();

    when(employeeClient.getEmployeeById(uuid.toString())).thenReturn(Optional.empty());

    Optional<String> result = employeeService.deleteEmployeeByIdOrName(uuid.toString());

    assertTrue(result.isEmpty());
    verify(employeeClient, never()).deleteEmployeeByName(any());
  }

  @Test
  void testDeleteEmployeeByIdOrName_plainName() {
    when(employeeClient.deleteEmployeeByName("Jane")).thenReturn(true);

    Optional<String> result = employeeService.deleteEmployeeByIdOrName("Jane");

    assertTrue(result.isPresent());
    assertEquals("Jane", result.get());
  }

  @Test
  void testDeleteEmployeeByIdOrName_notFound() {
    when(employeeClient.deleteEmployeeByName("NonExistent")).thenReturn(false);

    Optional<String> result = employeeService.deleteEmployeeByIdOrName("NonExistent");

    assertTrue(result.isEmpty());
  }

  @Test
  void cacheExpiresAfterTTL() {
    var employeesV1 =
        List.of(new Employee(UUID.randomUUID(), "A", 1000, 30, "Dev", "a@company.com"));
    var employeesV2 =
        List.of(new Employee(UUID.randomUUID(), "B", 2000, 40, "Mgr", "b@company.com"));
    when(employeeClient.getAllEmployees()).thenReturn(employeesV1).thenReturn(employeesV2);

    // First call — should fetch from API
    var result1 = employeeService.getAllEmployees();
    verify(employeeClient, times(1)).getAllEmployees();
    assertThat(result1).containsExactlyElementsOf(employeesV1);

    // Advance 1 minute — still within TTL (2 minutes)
    clock.advance(Duration.ofMinutes(1));
    var result2 = employeeService.getAllEmployees();
    verify(employeeClient, times(1)).getAllEmployees(); // Still cached
    assertThat(result2).isEqualTo(result1); // FIXED: Use isEqualTo, not isSameAs

    // Advance beyond TTL (total 3 minutes now)
    clock.advance(Duration.ofMinutes(2));
    var result3 = employeeService.getAllEmployees();
    verify(employeeClient, times(2)).getAllEmployees(); // Refetched
    assertThat(result3).containsExactlyElementsOf(employeesV2);
  }

  @Test
  void searchByName_usesCachedData() {
    var employees =
        List.of(
            new Employee(UUID.randomUUID(), "John Doe", 1000, 30, "Dev", "john@company.com"),
            new Employee(UUID.randomUUID(), "Jane Smith", 2000, 35, "Manager", "jane@company.com"));
    when(employeeClient.getAllEmployees()).thenReturn(employees);

    // Search should use cache
    List<Employee> results = employeeService.searchByName("john");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getEmployeeName()).isEqualTo("John Doe");
    verify(employeeClient, times(1)).getAllEmployees();

    // Second search should still use same cache
    results = employeeService.searchByName("jane");
    assertThat(results).hasSize(1);
    verify(employeeClient, times(1)).getAllEmployees(); // Still only 1 call
  }

  @Test
  void getHighestSalary_usesCachedData() {
    var employees =
        List.of(
            new Employee(UUID.randomUUID(), "John", 1000, 30, "Dev", "john@company.com"),
            new Employee(UUID.randomUUID(), "Jane", 5000, 35, "Manager", "jane@company.com"),
            new Employee(UUID.randomUUID(), "Bob", 3000, 40, "Senior Dev", "bob@company.com"));
    when(employeeClient.getAllEmployees()).thenReturn(employees);

    Optional<Integer> highest = employeeService.getHighestSalary();

    assertThat(highest).isPresent();
    assertThat(highest.get()).isEqualTo(5000);
    verify(employeeClient, times(1)).getAllEmployees();
  }

  @Test
  void invalidateCache_clearsCache() {
    var employees =
        List.of(new Employee(UUID.randomUUID(), "John", 1000, 30, "Dev", "john@company.com"));
    when(employeeClient.getAllEmployees()).thenReturn(employees);

    // Prime cache
    employeeService.getAllEmployees();
    verify(employeeClient, times(1)).getAllEmployees();

    // Manually invalidate
    employeeService.invalidateCache();

    // Next call should refetch
    employeeService.getAllEmployees();
    verify(employeeClient, times(2)).getAllEmployees();
  }
}
