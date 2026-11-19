package com.payrollportability.adapter.outbound.persistence;

import com.payrollportability.application.port.outbound.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "api")
public class SpringUnitOfWork implements UnitOfWork {
  private final TransactionTemplate template;

  public SpringUnitOfWork(TransactionTemplate template) {
    this.template = template;
  }

  public <T> T execute(Supplier<T> action) {
    return template.execute(status -> action.get());
  }
}
