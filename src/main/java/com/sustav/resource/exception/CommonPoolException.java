package com.sustav.resource.exception;

import lombok.Getter;

@Getter
public class CommonPoolException extends RuntimeException {

  private final String code;
  private final String message;

  public CommonPoolException(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public static CommonPoolException from(ErrorCode errorCode) {
    return new CommonPoolException(errorCode.name(), errorCode.getMessage());
  }

}
