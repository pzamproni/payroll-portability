package com.payrollportability.application.port.inbound;

import java.util.List;

public interface OperationProcessing {
  List<String> dueIds();

  void process(String id);
}
