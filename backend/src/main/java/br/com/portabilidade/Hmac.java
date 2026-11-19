package br.com.portabilidade;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Hmac {
  private final byte[] secret;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public Hmac(@Value("${app.hmac-secret}") String secret) {
    this(secret, Clock.systemUTC());
  }

  Hmac(String secret, Clock clock) {
    if (secret.length() < 32)
      throw new IllegalArgumentException("HMAC_SECRET deve ter pelo menos 32 caracteres");
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.clock = clock;
  }

  public static String hash(String body) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public String sign(String canonical) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public String request(
      String method, String path, String timestamp, String nonce, String key, String body) {
    return method
        + "\n"
        + path
        + "\n"
        + timestamp
        + "\n"
        + nonce
        + "\n"
        + (key == null ? "" : key)
        + "\n"
        + hash(body);
  }

  public String response(int status, String timestamp, String nonce, String body) {
    return status + "\n" + timestamp + "\n" + nonce + "\n" + hash(body);
  }

  public boolean verify(String canonical, String signature, String timestamp) {
    try {
      if (Math.abs(
              Duration.between(Instant.ofEpochSecond(Long.parseLong(timestamp)), clock.instant())
                  .getSeconds())
          > 300) return false;
      return MessageDigest.isEqual(
          HexFormat.of().parseHex(sign(canonical)), HexFormat.of().parseHex(signature));
    } catch (RuntimeException e) {
      return false;
    }
  }
}
