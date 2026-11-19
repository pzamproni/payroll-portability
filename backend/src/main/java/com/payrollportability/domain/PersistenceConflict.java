package com.payrollportability.domain;

/** A persistence constraint rejected a concurrent change; independent of the database vendor. */
public class PersistenceConflict extends RuntimeException {
  public PersistenceConflict(Throwable cause) {
    super("Concurrent persistence conflict", cause);
  }
}
