package com.payrollportability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SourceConventionsTest {
  @Test
  void javaImportsAreExplicit() throws Exception {
    Pattern wildcard = Pattern.compile("\\bimport\\s+(?:static\\s+)?[\\w$.]+\\.\\*\\s*;");
    var violations = new ArrayList<String>();
    try (var sources = Files.walk(Path.of("src"))) {
      for (Path source : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
        if (wildcard.matcher(Files.readString(source)).find()) violations.add(source.toString());
      }
    }
    assertThat(violations).as("Production and test code must use explicit Java imports").isEmpty();
  }
}
