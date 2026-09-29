package io.github.chansan.example.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 智能记录 API 调用摘要，过滤高频重复分片刷屏，仅在关键里程碑与异常时输出清晰日志。 */
public final class ApiLoggingFilter implements Filter {
  private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;
    long startedAt = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      int status = res.getStatus();
      long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
      String method = req.getMethod();
      String uri = req.getRequestURI();
      String reqId = valueOrDash(res.getHeader("X-Request-Id"));

      if (status >= 400) {
        log.warn(
            "API {} {} -> HTTP {} ({}ms) [requestId:{}]", method, uri, status, durationMs, reqId);
      } else if (isMilestone(method, uri)) {
        log.info(
            "API {} {} -> HTTP {} ({}ms) [requestId:{}]", method, uri, status, durationMs, reqId);
      } else {
        log.debug("API {} {} -> HTTP {} ({}ms)", method, uri, status, durationMs);
      }
    }
  }

  private static boolean isMilestone(String method, String uri) {
    if ("DELETE".equalsIgnoreCase(method)) return true;
    if ("POST".equalsIgnoreCase(method)) {
      return uri.endsWith("/uploads") || uri.endsWith("/complete") || uri.endsWith("/files");
    }
    return false;
  }

  private static String valueOrDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }
}
