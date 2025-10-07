package com.reliaquest.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, String status, String error) {
  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(data, "ok", null);
  }

  public static <T> ApiResponse<T> error(String message) {
    return new ApiResponse<>(null, "error", message);
  }
}
