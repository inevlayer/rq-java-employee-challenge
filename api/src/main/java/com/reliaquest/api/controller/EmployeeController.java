package com.reliaquest.api.controller;

import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/employee")
@Tag(name = "Employee Management", description = "APIs for managing employees")
public class EmployeeController implements IEmployeeController<Employee, CreateEmployeeInput> {

  private final EmployeeService employeeService;

  @Override
  @GetMapping()
  @Operation(summary = "Get all employees")
  public ResponseEntity<List<Employee>> getAllEmployees() {
    List<Employee> employees = employeeService.getAllEmployees();
    return ResponseEntity.ok(employees);
  }

  @Override
  @GetMapping("/search/{searchString}")
  @Operation(summary = "Search employees by name")
  public ResponseEntity<List<Employee>> getEmployeesByNameSearch(
      @Parameter(description = "Search string", required = true) @PathVariable
          String searchString) {
    List<Employee> results = employeeService.searchByName(searchString);
    if (results.isEmpty()) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(results);
  }

  @Override
  @GetMapping("/{id}")
  @Operation(summary = "Get employee by ID")
  public ResponseEntity<Employee> getEmployeeById(
      @Parameter(description = "Employee ID", required = true) @PathVariable String id) {
    return employeeService
        .getEmployeeById(id)
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new NoSuchElementException("Employee not found with ID: " + id));
  }

  @Override
  @GetMapping("/highestSalary")
  @Operation(summary = "Get highest salary")
  public ResponseEntity<Integer> getHighestSalaryOfEmployees() {
    return employeeService
        .getHighestSalary()
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Override
  @GetMapping("/topTenHighestEarningEmployeeNames")
  @Operation(summary = "Get top 10 highest earning employees")
  public ResponseEntity<List<String>> getTopTenHighestEarningEmployeeNames() {
    List<String> names = employeeService.getTopTenHighestEarningNames();
    if (names.isEmpty()) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(names);
  }

  @Override
  @PostMapping()
  @Operation(summary = "Create new employee")
  public ResponseEntity<Employee> createEmployee(
      @Parameter(description = "Employee details", required = true) @RequestBody @Valid
          CreateEmployeeInput employeeInput) {
    return employeeService
        .createEmployee(employeeInput)
        .map(e -> ResponseEntity.status(HttpStatus.CREATED).body(e))
        .orElseThrow(() -> new IllegalArgumentException("Failed to create employee"));
  }

  @Override
  @DeleteMapping("/{id}")
  @Operation(summary = "Delete employee by ID or name")
  public ResponseEntity<String> deleteEmployeeById(
      @Parameter(description = "Employee ID or name", required = true) @PathVariable String id) {
    return employeeService
        .deleteEmployeeByName(id)
        .map(name -> ResponseEntity.ok("Successfully deleted employee: " + name))
        .orElseThrow(() -> new NoSuchElementException("Employee not found: " + id));
  }
}
