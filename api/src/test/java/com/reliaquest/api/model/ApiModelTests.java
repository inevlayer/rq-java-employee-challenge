package com.reliaquest.api.model;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiModelTests {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void apiResponse_successAndErrorFactories_workAsExpected() {
    ApiResponse<String> ok = ApiResponse.success("data");
    assertEquals("ok", ok.status());
    assertEquals("data", ok.data());
    assertNull(ok.error());

    ApiResponse<Void> err = ApiResponse.error("bad request");
    assertEquals("error", err.status());
    assertEquals("bad request", err.error());
    assertNull(err.data());
  }

  @Test
  void employee_serializesWithSnakeCase() throws Exception {
    Employee employee =
        Employee.builder()
            .id(UUID.randomUUID())
            .employeeName("Alice")
            .employeeSalary(1000)
            .employeeAge(30)
            .employeeTitle("Engineer")
            .employeeEmail("alice@example.com")
            .build();

    String json = mapper.writeValueAsString(employee);
    assertTrue(json.contains("employee_name"));
    assertTrue(json.contains("employee_salary"));
    assertTrue(json.contains("employee_age"));
  }

  @Test
  void createEmployeeInput_hasValidFields() {
    CreateEmployeeInput input = new CreateEmployeeInput();
    input.setName("John");
    input.setSalary(5000);
    input.setAge(25);
    input.setTitle("Manager");

    assertEquals("John", input.getName());
    assertEquals(5000, input.getSalary());
    assertEquals(25, input.getAge());
    assertEquals("Manager", input.getTitle());
  }

  @Test
  void deleteEmployeeInput_storesNameProperly() {
    DeleteEmployeeInput input = new DeleteEmployeeInput("Bob");
    assertEquals("Bob", input.getName());
  }
}
