package cn.bugstack.trigger.support;

import cn.bugstack.domain.order.service.IOrderService;
import cn.bugstack.trigger.metrics.PaymentCallbackMetrics;
import com.alibaba.fastjson.JSON;
import com.alipay.api.internal.util.AlipaySignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class AlipayNotifySupport {

    @Value("${alipay.alipay_public_key}")
    private String alipayPublicKey;

    @Resource
    private IOrderService orderService;

    @Resource
    private PaymentCallbackMetrics paymentCallbackMetrics;

    @Resource
    private StructuredBusinessLogger businessLogger;

    public String handle(HttpServletRequest request) {
        return handle(request, System.currentTimeMillis());
    }

    public String handle(HttpServletRequest request, long startMillis) {
        try {
            return handleChecked(request, startMillis);
        } catch (Exception e) {
            log.error("支付回调处理失败 outTradeNo:{}", null == request ? null : request.getParameter("out_trade_no"), e);
            businessLogger.error("mall_pay_notify", "system_error", businessLogger.fields(
                    "tradeStatus", null == request ? null : request.getParameter("trade_status"),
                    "outTradeNo", null == request ? null : request.getParameter("out_trade_no"),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return "false";
        }
    }

    private String handleChecked(HttpServletRequest request, long startMillis) throws Exception {
        String tradeStatus = request.getParameter("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus)) {
            paymentCallbackMetrics.recordFail("trade_status");
            businessLogger.warn("mall_pay_notify", "ignored_trade_status", businessLogger.fields(
                    "tradeStatus", tradeStatus,
                    "outTradeNo", request.getParameter("out_trade_no"),
                    "costMs", System.currentTimeMillis() - startMillis));
            return "false";
        }

        Map<String, String> params = extractParams(request);
        String outTradeNo = params.get("out_trade_no");
        String alipayTradeNo = params.get("trade_no");
        if (!checkSignature(params)) {
            paymentCallbackMetrics.recordFail("signature");
            businessLogger.warn("mall_pay_notify", "signature_failed", businessLogger.fields(
                    "tradeStatus", tradeStatus,
                    "outTradeNo", outTradeNo,
                    "alipayTradeNo", alipayTradeNo,
                    "costMs", System.currentTimeMillis() - startMillis));
            return "false";
        }

        orderService.changeOrderPaySuccess(outTradeNo,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(params.get("gmt_payment")),
                "alipay",
                alipayTradeNo,
                JSON.toJSONString(params));

        businessLogger.info("mall_pay_notify", "success", businessLogger.fields(
                "tradeStatus", tradeStatus,
                "outTradeNo", outTradeNo,
                "alipayTradeNo", alipayTradeNo,
                "payChannel", "alipay",
                "costMs", System.currentTimeMillis() - startMillis));
        return "success";
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (String name : requestParams.keySet()) {
            params.put(name, request.getParameter(name));
        }
        return params;
    }

    private boolean checkSignature(Map<String, String> params) throws Exception {
        String sign = params.get("sign");
        String content = AlipaySignature.getSignCheckContentV1(params);
        return AlipaySignature.rsa256CheckContent(content, sign, alipayPublicKey, "UTF-8");
    }

}
