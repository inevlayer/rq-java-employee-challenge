package com.reliaquest.api.service;

import com.reliaquest.api.client.EmployeeClient;
import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmployeeService {
  private final EmployeeClient employeeClient;
  private final Clock clock;

  private List<Employee> cachedEmployees;
  private Instant lastRefreshTime;
  private static final Duration CACHE_TTL = Duration.ofMinutes(2);

  // Primary constructor for production use
  @Autowired
  public EmployeeService(EmployeeClient employeeClient) {
    this(employeeClient, Clock.systemUTC());
  }

  // Constructor for testing with custom clock
  public EmployeeService(EmployeeClient employeeClient, Clock clock) {
    this.employeeClient = employeeClient;
    this.clock = clock;
  }

  private boolean cacheExpired() {
    if (cachedEmployees == null || lastRefreshTime == null) return true;
    Duration elapsed = Duration.between(lastRefreshTime, Instant.now(clock));
    return elapsed.compareTo(CACHE_TTL) > 0;
  }

  public synchronized List<Employee> getAllEmployeesCached() {
    if (cacheExpired()) {
      log.debug("Cache expired or empty, fetching employees from API...");
      cachedEmployees = employeeClient.getAllEmployees();
      lastRefreshTime = Instant.now(clock);
      log.debug(
          "Fetched {} employees from API", cachedEmployees != null ? cachedEmployees.size() : 0);
    } else {
      log.debug(
          "Using cached employee list ({} entries)",
          cachedEmployees != null ? cachedEmployees.size() : 0);
    }
    return cachedEmployees != null ? new ArrayList<>(cachedEmployees) : Collections.emptyList();
  }

  public synchronized void invalidateCache() {
    log.debug("Invalidating employee cache");
    cachedEmployees = null;
    lastRefreshTime = null;
  }

  public List<Employee> getAllEmployees() {
    return getAllEmployeesCached();
  }

  public List<Employee> searchByName(String searchString) {
    if (searchString == null || searchString.isBlank()) {
      return Collections.emptyList();
    }
    String lower = searchString.toLowerCase(Locale.ROOT);
    return getAllEmployeesCached().stream()
        .filter(
            e ->
                e.getEmployeeName() != null
                    && e.getEmployeeName().toLowerCase(Locale.ROOT).contains(lower))
        .collect(Collectors.toList());
  }

  public Optional<Employee> getEmployeeById(String id) {
    if (id == null || id.isBlank()) {
      return Optional.empty();
    }
    try {
      return employeeClient.getEmployeeById(id);
    } catch (Exception e) {
      log.warn("Error fetching employee by ID {}: {}", id, e.getMessage());
      return Optional.empty();
    }
  }

  public Optional<Integer> getHighestSalary() {
    return getAllEmployeesCached().stream()
        .map(Employee::getEmployeeSalary)
        .filter(Objects::nonNull)
        .max(Integer::compareTo);
  }

  public List<String> getTopTenHighestEarningNames() {
    return getAllEmployeesCached().stream()
        .filter(e -> e.getEmployeeSalary() != null)
        .sorted(Comparator.comparing(Employee::getEmployeeSalary).reversed())
        .limit(10)
        .map(Employee::getEmployeeName)
        .toList();
  }

  public Optional<Employee> createEmployee(CreateEmployeeInput input) {
    if (input == null) {
      log.warn("Cannot create employee: input is null");
      return Optional.empty();
    }
    try {
      Optional<Employee> createdOpt = employeeClient.createEmployee(input);
      if (createdOpt.isPresent()) {
        invalidateCache(); // invalidate immediately
        Employee created = createdOpt.get();
        log.info("Created new employee: {}", created.getEmployeeName());
        return createdOpt;
      }
      log.warn("Employee creation returned empty result");
    } catch (Exception e) {
      log.error("Failed to create employee {}: {}", input.getName(), e.getMessage());
    }
    return Optional.empty();
  }

  /**
   * Delete employee by ID or name. If the parameter looks like a UUID, fetch the employee first to
   * get their name. Otherwise, treat it as a name and delete directly.
   */
  public Optional<String> deleteEmployeeByIdOrName(String idOrName) {
    if (idOrName == null || idOrName.isBlank()) {
      log.warn("Cannot delete employee: idOrName is null or blank");
      return Optional.empty();
    }

    // Check if it looks like a UUID
    if (isValidUUID(idOrName)) {
      log.debug("Parameter looks like UUID, fetching employee first: {}", idOrName);

      // Get employee by ID to find their name
      Optional<Employee> employeeOpt = getEmployeeById(idOrName);

      if (employeeOpt.isEmpty()) {
        log.warn("Employee not found for ID: {}", idOrName);
        return Optional.empty();
      }

      String employeeName = employeeOpt.get().getEmployeeName();
      log.debug("Found employee name '{}' for ID {}", employeeName, idOrName);

      // Now delete by name
      return deleteEmployeeByName(employeeName);
    } else {
      // Treat as name directly
      log.debug("Parameter treated as name: {}", idOrName);
      return deleteEmployeeByName(idOrName);
    }
  }

  public Optional<String> deleteEmployeeByName(String name) {
    if (name == null || name.isBlank()) {
      log.warn("Cannot delete employee: name is null or blank");
      return Optional.empty();
    }
    try {
      boolean deleted = employeeClient.deleteEmployeeByName(name);
      if (deleted) {
        invalidateCache(); // invalidate immediately - do not allow stale reads!
        log.debug("Deleted employee: {}", name);
        return Optional.of(name);
      }
      log.warn("Employee not found or could not be deleted: {}", name);
    } catch (Exception e) {
      log.error("Error deleting employee {}: {}", name, e.getMessage());
    }
    return Optional.empty();
  }

  private boolean isValidUUID(String str) {
    if (str == null) {
      return false;
    }
    try {
      UUID.fromString(str);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
