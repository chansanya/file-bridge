package io.github.chansan.example.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;

/** 维护请求追踪 ID 并写入响应头和 MDC。 */
public final class RequestIdFilter implements Filter {
  public static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest h = (HttpServletRequest) req;
    String id = h.getHeader("X-Request-Id");
    if (id == null || !id.matches("[A-Za-z0-9._-]{1,100}")) id = UUID.randomUUID().toString();
    req.setAttribute(ATTRIBUTE, id);
    ((HttpServletResponse) res).setHeader("X-Request-Id", id);
    try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", id)) {
      chain.doFilter(req, res);
    }
  }
}
