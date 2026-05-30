package cn.bugstack.trigger.support;

import cn.bugstack.api.response.Response;
import cn.bugstack.domain.order.service.IOrderService;
import cn.bugstack.types.common.Constants;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.request.AlipayTradeQueryRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Component
public class ActivePayNotifySupport {

    @Value("${mock-pay.enabled:false}")
    private boolean mockPayEnabled;

    @Resource
    private IOrderService orderService;

    @Resource
    private AlipayClient alipayClient;

    @Resource
    private StructuredBusinessLogger businessLogger;

    public Response<String> handle(String outTradeNo, long startMillis) {
        try {
            if (mockPayEnabled) {
                orderService.changeOrderPaySuccess(outTradeNo, new Date(), "mock", "MOCK:" + outTradeNo, "active_pay_notify_mock");
                businessLogger.info("mall_active_pay_notify", "mock_success", businessLogger.fields(
                        "outTradeNo", outTradeNo,
                        "payChannel", "mock",
                        "costMs", System.currentTimeMillis() - startMillis));
                return success("mock pay success");
            }

            AlipayTradeQueryModel bizModel = new AlipayTradeQueryModel();
            bizModel.setOutTradeNo(outTradeNo);

            AlipayTradeQueryRequest queryRequest = new AlipayTradeQueryRequest();
            queryRequest.setBizModel(bizModel);

            String body = alipayClient.execute(queryRequest).getBody();
            JSONObject queryResponse = JSON.parseObject(body).getJSONObject("alipay_trade_query_response");

            if (queryResponse != null && "10000".equals(queryResponse.getString("code"))) {
                return handleQuerySuccess(outTradeNo, body, queryResponse, startMillis);
            }

            String errorMsg = queryResponse != null ? queryResponse.getString("msg") : "查询失败";
            businessLogger.warn("mall_active_pay_notify", "alipay_query_failed", businessLogger.fields(
                    "outTradeNo", outTradeNo,
                    "errorMessage", errorMsg,
                    "costMs", System.currentTimeMillis() - startMillis));
            return error("查询失败: " + errorMsg);
        } catch (Exception e) {
            log.error("active pay notify failed, outTradeNo:{}", outTradeNo, e);
            businessLogger.error("mall_active_pay_notify", "system_error", businessLogger.fields(
                    "outTradeNo", outTradeNo,
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return error("系统异常: " + e.getMessage());
        }
    }

    private Response<String> handleQuerySuccess(String outTradeNo, String rawBody, JSONObject queryResponse, long startMillis) throws Exception {
        String tradeStatus = queryResponse.getString("trade_status");
        String tradeNo = queryResponse.getString("trade_no");
        String gmtPayment = queryResponse.getString("send_pay_date");

        if (!"TRADE_SUCCESS".equals(tradeStatus)) {
            businessLogger.warn("mall_active_pay_notify", "trade_not_success", businessLogger.fields(
                    "outTradeNo", outTradeNo,
                    "tradeStatus", tradeStatus,
                    "costMs", System.currentTimeMillis() - startMillis));
            return success("交易状态: " + tradeStatus);
        }

        orderService.changeOrderPaySuccess(outTradeNo,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(gmtPayment),
                "alipay_query",
                tradeNo,
                rawBody);

        businessLogger.info("mall_active_pay_notify", "alipay_query_success", businessLogger.fields(
                "outTradeNo", outTradeNo,
                "alipayTradeNo", tradeNo,
                "payChannel", "alipay_query",
                "costMs", System.currentTimeMillis() - startMillis));
        return success("交易成功，订单状态已更新");
    }

    private Response<String> success(String message) {
        return Response.<String>builder()
                .code(Constants.ResponseCode.SUCCESS.getCode())
                .info(Constants.ResponseCode.SUCCESS.getInfo())
                .data(message)
                .build();
    }

    private Response<String> error(String message) {
        return Response.<String>builder()
                .code(Constants.ResponseCode.UN_ERROR.getCode())
                .info(Constants.ResponseCode.UN_ERROR.getInfo())
                .data(message)
                .build();
    }

}
