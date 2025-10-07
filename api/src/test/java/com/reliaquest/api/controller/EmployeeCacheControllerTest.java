package com.reliaquest.api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reliaquest.api.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = EmployeeCacheController.class)
@ActiveProfiles("test")
class EmployeeCacheControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockBean private EmployeeService employeeService;

  @Test
  void invalidateCache_returns200AndCallsService() throws Exception {
    mockMvc
        .perform(post("/api/v1/employee/cache/invalidate"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Employee cache invalidated at")));

    verify(employeeService, times(1)).invalidateCache();
  }
}
