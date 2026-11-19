package com.payrollportability.adapter.inbound.scheduling;

import com.payrollportability.application.port.inbound.OperationProcessing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "api")
public class OperationScheduler {
  private static final Logger log = LoggerFactory.getLogger(OperationScheduler.class);
  private final OperationProcessing processor;

  public OperationScheduler(OperationProcessing processor) {
    this.processor = processor;
  }

  @Scheduled(
      fixedDelayString = "${app.worker-delay-ms}",
      initialDelayString = "${app.worker-delay-ms}")
  public void tick() {
    for (String id : processor.dueIds()) {
      try {
        processor.process(id);
      } catch (RuntimeException e) {
        log.error("Worker não concluiu operação {}: {}", id, e.getClass().getSimpleName());
      }
    }
  }
}
