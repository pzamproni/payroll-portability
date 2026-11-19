package com.payrollportability.application.port.outbound;

import java.util.function.Supplier;

public interface UnitOfWork {
  <T> T execute(Supplier<T> action);

  default void execute(Runnable action) {
    execute(
        () -> {
          action.run();
          return null;
        });
  }
}
