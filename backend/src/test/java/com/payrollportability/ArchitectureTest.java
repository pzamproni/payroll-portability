package com.payrollportability;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  private final JavaClasses production =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.payrollportability");

  @Test
  void coreHasNoFrameworkDependencies() {
    classes()
        .that()
        .resideInAnyPackage("..domain..", "..application..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(
            "java..", "com.payrollportability.domain..", "com.payrollportability.application..")
        .because(
            "business rules must be executable without Spring, JDBC, HTTP or serialization"
                + " libraries")
        .check(production);
  }

  @Test
  void inboundAdaptersOnlyUseApplicationPorts() {
    noClasses()
        .that()
        .resideInAPackage("..adapter.inbound..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..adapter.outbound..", "..application.service..", "..simulator..")
        .because("incoming requests must enter through use-case ports")
        .check(production);
  }

  @Test
  void outboundAdaptersDoNotDependOnUseCaseImplementations() {
    noClasses()
        .that()
        .resideInAPackage("..adapter.outbound..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..adapter.inbound..", "..application.service..", "..simulator..")
        .because(
            "infrastructure implements outbound ports instead of calling use-case implementations")
        .check(production);
  }
}
