package br.com.portabilidade;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
@ConditionalOnProperty(name = "app.role", havingValue = "central")
public class CentralSignatureFilter extends OncePerRequestFilter {
  private final Hmac hmac;

  public CentralSignatureFilter(Hmac hmac) {
    this.hmac = hmac;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var output = new ContentCachingResponseWrapper(response);
    byte[] bytes = request.getInputStream().readNBytes(65537);
    String body = new String(bytes, StandardCharsets.UTF_8);
    String ts = request.getHeader("X-Timestamp"),
        nonce = request.getHeader("X-Nonce"),
        key = request.getHeader("Idempotency-Key");
    if (bytes.length > 65536) {
      output.setStatus(413);
      output.setContentType("application/json");
      output.getWriter().write("{} ");
    } else if (nonce == null
        || nonce.length() > 100
        || !hmac.verify(
            hmac.request(request.getMethod(), request.getRequestURI(), ts, nonce, key, body),
            request.getHeader("X-Signature"),
            ts)) {
      output.setStatus(401);
      output.setContentType("application/json");
      output.getWriter().write("{} ");
    } else {
      var wrapped =
          new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
              var input = new ByteArrayInputStream(bytes);
              return new ServletInputStream() {
                public int read() {
                  return input.read();
                }

                public boolean isFinished() {
                  return input.available() == 0;
                }

                public boolean isReady() {
                  return true;
                }

                public void setReadListener(ReadListener listener) {
                  throw new UnsupportedOperationException();
                }
              };
            }

            @Override
            public BufferedReader getReader() {
              return new BufferedReader(
                  new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
          };
      chain.doFilter(wrapped, output);
    }
    String responseTs = Long.toString(Instant.now().getEpochSecond());
    output.setHeader("X-Timestamp", responseTs);
    output.setHeader(
        "X-Signature",
        Boolean.TRUE.equals(request.getAttribute("tamper"))
            ? "00"
            : hmac.sign(
                hmac.response(
                    output.getStatus(),
                    responseTs,
                    nonce,
                    new String(output.getContentAsByteArray(), StandardCharsets.UTF_8))));
    output.copyBodyToResponse();
  }
}
