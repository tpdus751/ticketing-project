package ticketing.common;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class TraceIdFilter implements Filter {
    public static final String HEADER = "Trace-Id"; // FE ↔ BE ↔ 로그/응답 공통 키

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpRes = (HttpServletResponse) res;

        // 🔹 현재 스팬에서 TraceId 추출 (Jaeger/OTel과 동일)
        String traceId = "NA";
        Span span = Span.current();
        SpanContext ctx = span.getSpanContext();
        if (ctx.isValid()) {
            traceId = ctx.getTraceId(); // 32자리 hex (Jaeger UI와 동일)
        }

        try {
            // 응답 헤더에 넣기 → FE roundtrip에도 같은 TraceId 반환
            httpRes.setHeader(HEADER, traceId);

            // Request attribute 저장 (원하면 Controller에서 꺼내쓸 수 있음)
            req.setAttribute(HEADER, traceId);

            // 로그 MDC에 traceId 심기 → %X{traceId}로 출력됨
            MDC.put("traceId", traceId);

            // 필터 체인 진행
            chain.doFilter(req, res);
        } finally {
            MDC.remove("traceId");
        }
    }
}
