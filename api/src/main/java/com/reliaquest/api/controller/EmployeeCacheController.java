package com.reliaquest.api.controller;

import com.reliaquest.api.service.EmployeeService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/v1/employee/cache")
@RequiredArgsConstructor
@Slf4j
public class EmployeeCacheController {

  private final EmployeeService employeeService;

  @PostMapping("/invalidate")
  public ResponseEntity<String> invalidateCache() {
    log.info("Manual cache invalidation triggered at {}", Instant.now());
    employeeService.invalidateCache();
    return ResponseEntity.ok("Employee cache invalidated at " + Instant.now());
  }
}
