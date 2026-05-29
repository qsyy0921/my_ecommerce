package cn.bugstack.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Optional HMAC verification for service-to-service trade APIs.
 */
@Slf4j
@Component
public class ServiceSignatureFilter extends OncePerRequestFilter {

    @Value("${app.security.service-sign.enabled:false}")
    private boolean enabled;
    @Value("${app.security.service-sign.app-id:s-pay-mall}")
    private String expectedAppId;
    @Value("${app.security.service-sign.secret:group-buy-market-dev-secret}")
    private String secret;
    @Value("${app.security.service-sign.max-skew-millis:300000}")
    private long maxSkewMillis;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/v1/gbm/trade/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String appId = request.getHeader("x-gbm-app-id");
        String timestamp = request.getHeader("x-gbm-timestamp");
        String nonce = request.getHeader("x-gbm-nonce");
        String signature = request.getHeader("x-gbm-signature");

        if (StringUtils.isAnyBlank(appId, timestamp, nonce, signature) || !expectedAppId.equals(appId)) {
            reject(response, "missing or illegal service signature headers");
            return;
        }

        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            reject(response, "illegal service signature timestamp");
            return;
        }

        if (Math.abs(System.currentTimeMillis() - requestTime) > maxSkewMillis) {
            reject(response, "expired service signature timestamp");
            return;
        }

        String expectedSignature = sign(request.getMethod(), request.getRequestURI(), timestamp, nonce);
        if (!constantTimeEquals(expectedSignature, signature)) {
            reject(response, "illegal service signature");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String reason) throws IOException {
        log.warn("reject service request: {}", reason);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"401\",\"info\":\"" + reason + "\"}");
    }

    private String sign(String method, String path, String timestamp, String nonce) {
        try {
            String payload = method + "\n" + path + "\n" + timestamp + "\n" + nonce;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("build service signature failed", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        int result = left.length() ^ right.length();
        int length = Math.min(left.length(), right.length());
        for (int i = 0; i < length; i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }
}
