package com.payrollportability.bootstrap;

import com.payrollportability.application.port.outbound.AgendaStateRepository;
import com.payrollportability.application.port.outbound.AuthorizationRepository;
import com.payrollportability.application.port.outbound.CentralGateway;
import com.payrollportability.application.port.outbound.OperationRepository;
import com.payrollportability.application.port.outbound.UnitOfWork;
import com.payrollportability.application.service.OperationProcessor;
import com.payrollportability.application.service.PortabilityService;
import com.payrollportability.application.service.RetryPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.role", havingValue = "api")
public class UseCaseConfiguration {
  @Bean
  PortabilityService portabilityService(
      AuthorizationRepository auth,
      AgendaStateRepository agenda,
      OperationRepository operations,
      CentralGateway central,
      UnitOfWork tx) {
    return new PortabilityService(auth, agenda, operations, central, tx);
  }

  @Bean
  RetryPolicy retryPolicy(
      @Value("${app.retry-base-ms}") long base,
      @Value("${app.retry-max-ms}") long max,
      @Value("${app.max-attempts}") int attempts) {
    return new RetryPolicy(base, max, attempts);
  }

  @Bean
  OperationProcessor operationProcessor(
      OperationRepository operations, CentralGateway central, UnitOfWork tx, RetryPolicy retry) {
    return new OperationProcessor(operations, central, tx, retry);
  }
}
