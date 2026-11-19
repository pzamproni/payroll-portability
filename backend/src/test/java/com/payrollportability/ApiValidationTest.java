package com.payrollportability;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payrollportability.application.port.outbound.CentralGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "app.role=api",
      "spring.datasource.url=jdbc:h2:mem:validation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.locations=classpath:db/migration",
      "app.hmac-secret=test-secret-with-at-least-32-characters",
      "app.worker-delay-ms=86400000"
    })
@AutoConfigureMockMvc
class ApiValidationTest {
  @Autowired MockMvc mvc;
  @MockitoBean CentralGateway central;

  String request(String bank, String scenario) {
    return """
    {"beneficiarioId":"BEN","parcelas":["p1"],"credorCessionario":"Credor",
     "contaRecebimento":{"banco":"%s","agencia":"1234","conta":"12345-6"},"cenario":"%s"}
    """
        .formatted(bank, scenario);
  }

  @Test
  void rejectsConsentFalse() throws Exception {
    mvc.perform(
            post("/api/autorizacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"beneficiarioId":"BEN","convenioId":"CONV","consentimento":false}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDACAO"));
    verifyNoInteractions(central);
  }

  @Test
  void rejectsInvalidAccount() throws Exception {
    mvc.perform(
            post("/api/operacoes")
                .header("Idempotency-Key", "valid-key-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("abc", "NORMAL")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDACAO"));
    verifyNoInteractions(central);
  }

  @Test
  void rejectsMissingHttpKey() throws Exception {
    mvc.perform(
            post("/api/operacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("001", "NORMAL")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CHAVE_INVALIDA"));
  }

  @Test
  void rejectsUnknownScenario() throws Exception {
    mvc.perform(
            post("/api/operacoes")
                .header("Idempotency-Key", "valid-key-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("001", "UNKNOWN")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("JSON_INVALIDO"));
  }

  @Test
  void unknownOperationReturns404() throws Exception {
    mvc.perform(get("/api/operacoes/missing-id"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NAO_ENCONTRADA"));
  }
}
