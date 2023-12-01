package com.sustav.resource.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

  POOL_IS_CLOSED("Pool is closed");

  private final String message;

}