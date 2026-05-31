package cn.bugstack.trigger.http;

import cn.bugstack.domain.order.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Date;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/mock-pay/")
public class MockPayController {

    @Resource
    private IOrderService orderService;

    @Value("${mock-pay.return-url:http://127.0.0.1:8088/order-list.html}")
    private String returnUrl;

    @RequestMapping(value = "confirm", method = {RequestMethod.GET, RequestMethod.POST}, produces = "text/html;charset=UTF-8")
    public String confirm(@RequestParam String outTradeNo,
                          @RequestParam(required = false) String payAmount,
                          @RequestParam(required = false) String subject) {
        log.info("mock pay confirm start outTradeNo:{} payAmount:{} subject:{}", outTradeNo, payAmount, subject);
        orderService.changeOrderPaySuccess(outTradeNo, new Date(), "mock", "MOCK:" + outTradeNo,
                "payAmount=" + payAmount + ";subject=" + subject);
        log.info("mock pay confirm success outTradeNo:{}", outTradeNo);

        return "<!DOCTYPE html>"
                + "<html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>模拟支付成功</title>"
                + "<style>"
                + "body{font-family:Arial,'Microsoft YaHei',sans-serif;background:#f5f7fb;margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;color:#1f2937;}"
                + ".box{width:420px;max-width:calc(100vw - 32px);background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:28px;box-shadow:0 12px 40px rgba(15,23,42,.12);}"
                + "h1{font-size:22px;margin:0 0 16px;}p{line-height:1.7;margin:8px 0;color:#4b5563;word-break:break-all;}.ok{color:#0f766e;font-weight:700;}"
                + "a{display:inline-block;margin-top:18px;background:#2563eb;color:#fff;text-decoration:none;padding:10px 16px;border-radius:6px;}"
                + "</style></head><body><div class=\"box\">"
                + "<h1>模拟支付成功</h1>"
                + "<p class=\"ok\">订单已按支付成功处理，无需调用支付宝。</p>"
                + "<p>订单号：" + escapeHtml(outTradeNo) + "</p>"
                + "<p>金额：" + escapeHtml(payAmount) + "</p>"
                + "<a href=\"" + escapeHtml(returnUrl) + "\">查看订单</a>"
                + "</div></body></html>";
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
