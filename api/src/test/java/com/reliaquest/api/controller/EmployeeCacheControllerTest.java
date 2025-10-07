package com.reliaquest.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.reliaquest.api.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = EmployeeCacheController.class)
@ActiveProfiles("test")
public class EmployeeCacheControllerTest {
  @Autowired private WebTestClient webTestClient;

  @MockBean private EmployeeService employeeService;

  @Test
  void invalidateCache_returns200AndCallsService() {
    webTestClient
        .post()
        .uri("/api/v1/employee/cache/invalidate")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("Employee cache invalidated at"));

    verify(employeeService, times(1)).invalidateCache();
  }
}
