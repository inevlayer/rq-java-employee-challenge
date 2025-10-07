package com.reliaquest.api.controller;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.reliaquest.api.model.CreateEmployeeInput;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.service.EmployeeService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientResponseException;

@WebMvcTest(controllers = EmployeeController.class)
class EmployeeControllerTest {
  private static final String ID = "cd52d69e-9878-4bac-a949-ca1bdecbe854";

  @Autowired private MockMvc mockMvc;

  @MockBean private EmployeeService employeeService;

  private Employee sampleEmployee;

  @BeforeEach
  void setup() {
    sampleEmployee = new Employee();
    sampleEmployee.setId(UUID.fromString(ID));
    sampleEmployee.setEmployeeName("Jane Doe");
    sampleEmployee.setEmployeeSalary(120000);
    sampleEmployee.setEmployeeAge(30);
    sampleEmployee.setEmployeeTitle("Software Engineer");
    sampleEmployee.setEmployeeEmail("jane.doe@company.com");
  }

  @Test
  void getAllEmployees_ShouldReturnList() throws Exception {
    Mockito.when(employeeService.getAllEmployees()).thenReturn(List.of(sampleEmployee));

    mockMvc
        .perform(get("/api/v1/employee"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].employee_name", is("Jane Doe")))
        .andExpect(jsonPath("$[0].employee_salary", is(120000)));
  }

  @Test
  void getAllEmployees_ShouldReturnEmptyList_WhenNoEmployees() throws Exception {
    Mockito.when(employeeService.getAllEmployees()).thenReturn(Collections.emptyList());

    mockMvc
        .perform(get("/api/v1/employee"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void getEmployeeById_ShouldReturnEmployee_WhenExists() throws Exception {
    Mockito.when(employeeService.getEmployeeById(ID)).thenReturn(Optional.of(sampleEmployee));

    mockMvc
        .perform(get("/api/v1/employee/" + ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employee_name", is("Jane Doe")))
        .andExpect(jsonPath("$.employee_salary", is(120000)))
        .andExpect(jsonPath("$.employee_age", is(30)))
        .andExpect(jsonPath("$.employee_title", is("Software Engineer")));
  }

  @Test
  void getEmployeeById_ShouldReturn404_WhenNotFound() throws Exception {
    Mockito.when(employeeService.getEmployeeById("missing")).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/v1/employee/missing")).andExpect(status().isNotFound());
  }

  @Test
  void getEmployeesByNameSearch_ShouldReturnMatches() throws Exception {
    Mockito.when(employeeService.searchByName("Jane")).thenReturn(List.of(sampleEmployee));

    mockMvc
        .perform(get("/api/v1/employee/search/Jane"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].employee_name", is("Jane Doe")));
  }

  @Test
  void getEmployeesByNameSearch_ShouldReturn204_WhenNoMatches() throws Exception {
    Mockito.when(employeeService.searchByName("Nonexistent")).thenReturn(Collections.emptyList());

    mockMvc.perform(get("/api/v1/employee/search/Nonexistent")).andExpect(status().isNoContent());
  }

  @Test
  void createEmployee_ShouldReturnCreated_WithValidInput() throws Exception {
    Mockito.when(employeeService.createEmployee(any(CreateEmployeeInput.class)))
        .thenReturn(Optional.of(sampleEmployee));

    String body =
        """
                        {
                          "name": "Jane Doe",
                          "age": 30,
                          "salary": 120000,
                          "title": "Software Engineer"
                        }
                        """;

    mockMvc
        .perform(post("/api/v1/employee").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.employee_name", is("Jane Doe")))
        .andExpect(jsonPath("$.employee_salary", is(120000)));
  }

  @Test
  void createEmployee_ShouldReturn400_WhenMissingRequiredFields() throws Exception {
    String body =
        """
                {
                  "name": "Jane Doe"
                }
                """;

    mockMvc
        .perform(post("/api/v1/employee").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createEmployee_ShouldReturn400_WhenInvalidAge() throws Exception {
    String body =
        """
                        {
                          "name": "Jane Doe",
                          "age": 15,
                          "salary": 120000,
                          "title": "Software Engineer"
                        }
                        """;

    mockMvc
        .perform(post("/api/v1/employee").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createEmployee_ShouldReturn400_WhenInvalidSalary() throws Exception {
    String body =
        """
                        {
                          "name": "Jane Doe",
                          "age": 30,
                          "salary": -1000,
                          "title": "Software Engineer"
                        }
                        """;

    mockMvc
        .perform(post("/api/v1/employee").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createEmployee_ShouldReturn400_WhenBlankName() throws Exception {
    String body =
        """
                        {
                          "name": "",
                          "age": 30,
                          "salary": 120000,
                          "title": "Software Engineer"
                        }
                        """;

    mockMvc
        .perform(post("/api/v1/employee").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createEmployee_ShouldReturn400_WhenServiceReturnsEmpty() throws Exception {
    Mockito.when(employeeService.createEmployee(any(CreateEmployeeInput.class)))
        .thenReturn(Optional.empty());

    String body =
        """
                        {
                          "name": "Jane Doe",
                          "age": 30,
                          "salary": 120000,
                          "title": "Software Engineer"
                        }
                        """;

    mockMvc
        .perform(post("/api/v1/employee").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getTopTenHighestEarningEmployeeNames_ShouldReturnNames() throws Exception {
    Mockito.when(employeeService.getTopTenHighestEarningNames())
        .thenReturn(List.of("Alice", "Bob", "Charlie"));

    mockMvc
        .perform(get("/api/v1/employee/topTenHighestEarningEmployeeNames"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)))
        .andExpect(jsonPath("$[0]", is("Alice")))
        .andExpect(jsonPath("$[1]", is("Bob")))
        .andExpect(jsonPath("$[2]", is("Charlie")));
  }

  @Test
  void getTopTenHighestEarningEmployeeNames_ShouldReturn204_WhenEmpty() throws Exception {
    Mockito.when(employeeService.getTopTenHighestEarningNames())
        .thenReturn(Collections.emptyList());

    mockMvc
        .perform(get("/api/v1/employee/topTenHighestEarningEmployeeNames"))
        .andExpect(status().isNoContent());
  }

  @Test
  void getHighestSalaryOfEmployees_ShouldReturnSalary() throws Exception {
    Mockito.when(employeeService.getHighestSalary()).thenReturn(Optional.of(150000));

    mockMvc
        .perform(get("/api/v1/employee/highestSalary"))
        .andExpect(status().isOk())
        .andExpect(content().string("150000"));
  }

  @Test
  void getHighestSalaryOfEmployees_ShouldReturn204_WhenNoEmployees() throws Exception {
    Mockito.when(employeeService.getHighestSalary()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/v1/employee/highestSalary")).andExpect(status().isNoContent());
  }

  @Test
  void deleteEmployeeById_ShouldReturnOk_WhenDeletedSimple() throws Exception {
    String employeeName = "Jane Doe";

    Mockito.when(employeeService.deleteEmployeeByIdOrName(eq(ID)))
        .thenReturn(Optional.of(employeeName));

    mockMvc
        .perform(delete("/api/v1/employee/" + ID))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("deleted")));
  }

  @Test
  void deleteEmployeeById_ShouldReturnOk_WhenDeleted() throws Exception {
    String employeeName = "Jane Doe";

    Mockito.when(employeeService.deleteEmployeeByIdOrName(eq(ID)))
        .thenReturn(Optional.of(employeeName));

    mockMvc
        .perform(delete("/api/v1/employee/" + ID))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("deleted")));
  }

  @Test
  void deleteEmployeeById_ShouldReturn404_WhenNotFound() throws Exception {
    Mockito.when(employeeService.deleteEmployeeByIdOrName(eq("nonexistent")))
        .thenReturn(Optional.empty());

    mockMvc.perform(delete("/api/v1/employee/nonexistent")).andExpect(status().isNotFound());
  }

  @Test
  void deleteEmployeeById_ShouldHandleServiceException() throws Exception {
    Mockito.when(employeeService.deleteEmployeeByIdOrName(any()))
        .thenThrow(new RuntimeException("Service error"));

    mockMvc.perform(delete("/api/v1/employee/" + ID)).andExpect(status().isInternalServerError());
  }

  @Test
  void getAllEmployees_ShouldHandleServiceException() throws Exception {
    Mockito.when(employeeService.getAllEmployees())
        .thenThrow(
            new RestClientResponseException(
                "Service error", 503, "Service Unavailable", null, null, null));

    mockMvc.perform(get("/api/v1/employee")).andExpect(status().isServiceUnavailable());
  }
}
