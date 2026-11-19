package com.payrollportability.domain;

public class DomainException extends RuntimeException {
  public enum Kind {
    INVALID_REQUEST,
    UNAUTHORIZED,
    NOT_FOUND,
    CONFLICT
  }

  private final Kind kind;
  private final String code;

  public DomainException(Kind kind, String code, String message) {
    super(message);
    this.kind = kind;
    this.code = code;
  }

  public Kind kind() {
    return kind;
  }

  public String code() {
    return code;
  }
}
