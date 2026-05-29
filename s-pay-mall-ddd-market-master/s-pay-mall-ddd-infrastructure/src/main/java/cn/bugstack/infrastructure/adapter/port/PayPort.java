package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.order.adapter.port.IPayPort;
import cn.bugstack.domain.order.model.exception.PayGatewayException;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;

@Component
public class PayPort implements IPayPort {

    @Value("${alipay.notify_url}")
    private String notifyUrl;
    @Value("${alipay.return_url}")
    private String returnUrl;
    @Value("${mock-pay.enabled:false}")
    private boolean mockPayEnabled;
    @Value("${mock-pay.confirm-url:http://127.0.0.1:8070/api/v1/mock-pay/confirm}")
    private String mockPayConfirmUrl;

    @Resource
    private AlipayClient alipayClient;

    @Override
    public String createPayForm(String orderId, BigDecimal payAmount, String productName, String payChannel) {
        if (useMockPay(payChannel)) {
            return buildMockPayForm(orderId, payAmount, productName);
        }

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(notifyUrl);
        request.setReturnUrl(returnUrl);

        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderId);
        bizContent.put("total_amount", payAmount);
        bizContent.put("subject", productName);
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        request.setBizContent(bizContent.toString());

        try {
            return alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            throw new PayGatewayException("create alipay form failed orderId:" + orderId, e);
        }
    }

    @Override
    public boolean refund(String orderId, BigDecimal payAmount) {
        if (mockPayEnabled) {
            return true;
        }

        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        AlipayTradeRefundModel refundModel = new AlipayTradeRefundModel();
        refundModel.setOutTradeNo(orderId);
        refundModel.setRefundAmount(payAmount.toString());
        refundModel.setRefundReason("交易退单");
        request.setBizModel(refundModel);

        try {
            AlipayTradeRefundResponse execute = alipayClient.execute(request);
            return execute.isSuccess();
        } catch (AlipayApiException e) {
            throw new PayGatewayException("refund alipay order failed orderId:" + orderId, e);
        }
    }

    private boolean useMockPay(String payChannel) {
        if ("alipay".equalsIgnoreCase(payChannel)) {
            return false;
        }
        if ("mock".equalsIgnoreCase(payChannel)) {
            return true;
        }
        return mockPayEnabled;
    }

    private String buildMockPayForm(String orderId, BigDecimal payAmount, String productName) {
        return "<form id=\"mock-pay-form\" data-pay-channel=\"mock\" method=\"post\" action=\"" + escapeHtml(mockPayConfirmUrl) + "\">"
                + "<input type=\"hidden\" name=\"outTradeNo\" value=\"" + escapeHtml(orderId) + "\"/>"
                + "<input type=\"hidden\" name=\"payAmount\" value=\"" + escapeHtml(payAmount.toPlainString()) + "\"/>"
                + "<input type=\"hidden\" name=\"subject\" value=\"" + escapeHtml(productName) + "\"/>"
                + "</form>";
    }

    private String escapeHtml(String value) {
        if (null == value) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

}
