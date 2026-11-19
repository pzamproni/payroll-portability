package com.payrollportability;

import static org.assertj.core.api.Assertions.assertThat;

import com.payrollportability.infrastructure.security.Hmac;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class HmacTest {
  final Instant now = Instant.parse("2026-11-19T10:00:00Z");
  final Hmac hmac =
      new Hmac("a-secret-with-at-least-32-characters", Clock.fixed(now, ZoneOffset.UTC));
  final String ts = Long.toString(now.getEpochSecond());

  @Test
  void signsAndVerifiesExactBytes() {
    String value = hmac.request("POST", "/operacoes", ts, "nonce", "key", "{}");
    assertThat(hmac.verify(value, hmac.sign(value), ts)).isTrue();
  }

  @Test
  void rejectsTamperedBody() {
    String value = hmac.request("POST", "/operacoes", ts, "nonce", "key", "{}");
    assertThat(hmac.verify(value + "x", hmac.sign(value), ts)).isFalse();
  }

  @Test
  void rejectsExpiredTimestamp() {
    String old = Long.toString(now.minusSeconds(301).getEpochSecond());
    assertThat(hmac.verify("body", hmac.sign("body"), old)).isFalse();
  }

  @Test
  void rejectsFutureTimestamp() {
    String future = Long.toString(now.plusSeconds(301).getEpochSecond());
    assertThat(hmac.verify("body", hmac.sign("body"), future)).isFalse();
  }

  @Test
  void rejectsMalformedSignature() {
    assertThat(hmac.verify("body", "not-hex", ts)).isFalse();
    assertThat(hmac.verify("body", null, ts)).isFalse();
  }

  @Test
  void responseIsBoundToRequestNonceAndStatus() {
    String signed = hmac.sign(hmac.response(202, ts, "one", "{}"));
    assertThat(hmac.verify(hmac.response(202, ts, "two", "{}"), signed, ts)).isFalse();
    assertThat(hmac.verify(hmac.response(200, ts, "one", "{}"), signed, ts)).isFalse();
  }

  @Test
  void requestBindsMethodPathAndIdempotencyKey() {
    String value = hmac.request("POST", "/a", ts, "nonce", "key1", "{}");
    assertThat(
            hmac.verify(hmac.request("GET", "/a", ts, "nonce", "key1", "{}"), hmac.sign(value), ts))
        .isFalse();
    assertThat(
            hmac.verify(
                hmac.request("POST", "/b", ts, "nonce", "key1", "{}"), hmac.sign(value), ts))
        .isFalse();
    assertThat(
            hmac.verify(
                hmac.request("POST", "/a", ts, "nonce", "key2", "{}"), hmac.sign(value), ts))
        .isFalse();
  }
}
