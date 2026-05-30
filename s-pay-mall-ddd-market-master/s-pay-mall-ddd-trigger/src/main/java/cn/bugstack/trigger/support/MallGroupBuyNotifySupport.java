package cn.bugstack.trigger.support;

import cn.bugstack.api.dto.NotifyRequestDTO;
import cn.bugstack.domain.order.service.IOrderService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class MallGroupBuyNotifySupport {

    @Resource
    private IOrderService orderService;

    @Resource
    private StructuredBusinessLogger businessLogger;

    public String handle(NotifyRequestDTO request) {
        long startMillis = System.currentTimeMillis();
        log.info("拼团回调，组队完成，结算开始 {}", JSON.toJSONString(request));
        try {
            if (null == request || null == request.getOutTradeNoList()) {
                businessLogger.warn("mall_group_buy_notify", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return "error";
            }

            orderService.changeOrderMarketSettlement(request.getOutTradeNoList());
            businessLogger.info("mall_group_buy_notify", "success", businessLogger.fields(
                    "teamId", request.getTeamId(),
                    "outTradeNoCount", request.getOutTradeNoList().size(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return "success";
        } catch (Exception e) {
            log.error("拼团回调，组队完成，结算失败 {}", JSON.toJSONString(request), e);
            businessLogger.error("mall_group_buy_notify", "system_error", businessLogger.fields(
                    "teamId", null == request ? null : request.getTeamId(),
                    "outTradeNoCount", null == request || null == request.getOutTradeNoList() ? 0 : request.getOutTradeNoList().size(),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return "error";
        }
    }

}
