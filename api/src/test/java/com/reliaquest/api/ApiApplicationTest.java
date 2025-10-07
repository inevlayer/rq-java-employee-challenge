package com.reliaquest.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.reliaquest.api.client.EmployeeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ApiApplicationTest {

  @Autowired private EmployeeClient employeeClient;

  @Test
  void contextLoads() {
    assertThat(employeeClient).isNotNull();
  }
}
