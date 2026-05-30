package cn.bugstack.trigger.http;

import cn.bugstack.api.IPayService;
import cn.bugstack.api.dto.CreatePayRequestDTO;
import cn.bugstack.api.dto.NotifyRequestDTO;
import cn.bugstack.api.dto.QueryOrderListRequestDTO;
import cn.bugstack.api.dto.QueryOrderListResponseDTO;
import cn.bugstack.api.dto.RefundOrderRequestDTO;
import cn.bugstack.api.dto.RefundOrderResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.trigger.support.ActivePayNotifySupport;
import cn.bugstack.trigger.support.AlipayNotifySupport;
import cn.bugstack.trigger.support.MallGroupBuyNotifySupport;
import cn.bugstack.trigger.support.MallOrderQuerySupport;
import cn.bugstack.trigger.support.MallPayOrderCreateSupport;
import cn.bugstack.trigger.support.MallRefundOrderSupport;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/alipay/")
public class AliPayController implements IPayService {

    @Resource
    private MallPayOrderCreateSupport mallPayOrderCreateSupport;

    @Resource
    private MallGroupBuyNotifySupport mallGroupBuyNotifySupport;

    @Resource
    private MallOrderQuerySupport mallOrderQuerySupport;

    @Resource
    private MallRefundOrderSupport mallRefundOrderSupport;

    @Resource
    private AlipayNotifySupport alipayNotifySupport;

    @Resource
    private ActivePayNotifySupport activePayNotifySupport;

    /**
     * http://localhost:8080/api/v1/alipay/create_pay_order
     * <p>
     * {
     * "userId": "10001",
     * "productId": "100001"
     * }
     */
    @RequestMapping(value = "create_pay_order", method = RequestMethod.POST)
    @Override
    public Response<String> createPayOrder(@RequestBody CreatePayRequestDTO createPayRequestDTO) {
        return mallPayOrderCreateSupport.create(createPayRequestDTO);
    }

    @RequestMapping(value = "group_buy_notify", method = RequestMethod.POST)
    @Override
    public String groupBuyNotify(@RequestBody NotifyRequestDTO requestDTO) {
        return mallGroupBuyNotifySupport.handle(requestDTO);
    }

    /**
     * http://xfg-studio.natapp1.cc/api/v1/alipay/alipay_notify_url
     */
    @RequestMapping(value = "alipay_notify_url", method = RequestMethod.POST)
    public String payNotify(HttpServletRequest request) {
        return alipayNotifySupport.handle(request);
    }

    /**
     * http://localhost:8080/api/v1/alipay/query_user_order_list
     * <p>
     * {
     * "userId": "10001",
     * "lastId": null,
     * "pageSize": 10
     * }
     */
    @RequestMapping(value = "query_user_order_list", method = RequestMethod.POST)
    @Override
    public Response<QueryOrderListResponseDTO> queryUserOrderList(@RequestBody QueryOrderListRequestDTO requestDTO) {
        return mallOrderQuerySupport.queryUserOrderList(requestDTO);
    }

    /**
     * http://localhost:8080/api/v1/alipay/refund_order
     * <p>
     * {
     * "userId": "xfg02",
     * "orderId": "928263928388"
     * }
     */
    @RequestMapping(value = "refund_order", method = RequestMethod.POST)
    @Override
    public Response<RefundOrderResponseDTO> refundOrder(@RequestBody RefundOrderRequestDTO requestDTO) {
        return mallRefundOrderSupport.refund(requestDTO);
    }

    /**
     * 测试回调接口 - 主动查询支付宝交易状态
     *
     * @param outTradeNo 商户订单号
     * @return 处理结果
     */
    @RequestMapping(value = "active_pay_notify", method = RequestMethod.POST)
    public Response<String> activePayNotify(@RequestParam String outTradeNo) {
        return activePayNotifySupport.handle(outTradeNo);
    }

}
