package com.payrollportability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payrollportability.domain.Models.Account;
import com.payrollportability.domain.Models.Event;
import com.payrollportability.domain.Models.Operation;
import com.payrollportability.domain.Models.OperationRequest;
import com.payrollportability.domain.Models.Scenario;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelsTest {
  @Test
  void operationEventsAreImmutableSnapshots() {
    var event = new Event(1, Instant.now(), "SOLICITADA", "Request persisted");
    var mutable = new ArrayList<Event>();
    mutable.add(event);
    var request =
        new OperationRequest(
            "BEN", List.of("p1"), "Credor", new Account("001", "1", "2-3"), Scenario.NORMAL);
    var op =
        new Operation(
            "op", "BEN", "RECEBIDA", null, 0, Instant.now(), null, Instant.now(), request, mutable);
    mutable.clear();
    assertThat(op.eventos()).containsExactly(event);
    assertThatThrownBy(() -> op.eventos().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
