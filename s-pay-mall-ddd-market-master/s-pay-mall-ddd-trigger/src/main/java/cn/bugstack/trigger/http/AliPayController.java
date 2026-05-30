package cn.bugstack.trigger.http;

import cn.bugstack.api.IPayService;
import cn.bugstack.api.dto.CreatePayRequestDTO;
import cn.bugstack.api.dto.NotifyRequestDTO;
import cn.bugstack.api.dto.QueryOrderListRequestDTO;
import cn.bugstack.api.dto.QueryOrderListResponseDTO;
import cn.bugstack.api.dto.RefundOrderRequestDTO;
import cn.bugstack.api.dto.RefundOrderResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.PayOrderEntity;
import cn.bugstack.domain.order.model.entity.ShopCartEntity;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import cn.bugstack.domain.order.service.IOrderService;
import cn.bugstack.trigger.support.ActivePayNotifySupport;
import cn.bugstack.trigger.support.AlipayNotifySupport;
import cn.bugstack.trigger.support.OrderListResponseAssembler;
import cn.bugstack.trigger.support.StructuredBusinessLogger;
import cn.bugstack.types.common.Constants;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/alipay/")
public class AliPayController implements IPayService {

    @Resource
    private IOrderService orderService;
    @Resource
    private AlipayNotifySupport alipayNotifySupport;
    @Resource
    private ActivePayNotifySupport activePayNotifySupport;
    @Resource
    private OrderListResponseAssembler orderListResponseAssembler;
    @Resource
    private StructuredBusinessLogger businessLogger;

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
        long startMillis = System.currentTimeMillis();
        try {
            if (null == createPayRequestDTO) {
                businessLogger.warn("mall_create_pay_order", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<String>builder()
                        .code(Constants.ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(Constants.ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }
            log.info("商品下单，根据商品ID创建支付单开始 userId:{} productId:{}", createPayRequestDTO.getUserId(), createPayRequestDTO.getUserId());
            String userId = createPayRequestDTO.getUserId();
            String productId = createPayRequestDTO.getProductId();
            String teamId = createPayRequestDTO.getTeamId();
            Integer marketType = createPayRequestDTO.getMarketType();

            // 下单
            PayOrderEntity payOrderEntity = orderService.createOrder(ShopCartEntity.builder()
                    .userId(userId)
                    .productId(productId)
                    .teamId(teamId)
                    .marketTypeVO(MarketTypeVO.valueOf(marketType))
                    .activityId(createPayRequestDTO.getActivityId())
                    .payChannel(createPayRequestDTO.getPayChannel())
                    .build());

            log.info("商品下单，根据商品ID创建支付单完成 userId:{} productId:{} orderId:{}", userId, productId, payOrderEntity.getOrderId());
            businessLogger.info("mall_create_pay_order", "success", businessLogger.fields(
                    "userId", userId,
                    "productId", productId,
                    "orderId", payOrderEntity.getOrderId(),
                    "teamId", teamId,
                    "activityId", createPayRequestDTO.getActivityId(),
                    "marketType", marketType,
                    "payChannel", createPayRequestDTO.getPayChannel(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<String>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(payOrderEntity.getPayUrl())
                    .build();
        } catch (Exception e) {
            log.error("商品下单，根据商品ID创建支付单失败 userId:{} productId:{}", null == createPayRequestDTO ? null : createPayRequestDTO.getUserId(), null == createPayRequestDTO ? null : createPayRequestDTO.getProductId(), e);
            businessLogger.error("mall_create_pay_order", "system_error", businessLogger.fields(
                    "userId", null == createPayRequestDTO ? null : createPayRequestDTO.getUserId(),
                    "productId", null == createPayRequestDTO ? null : createPayRequestDTO.getProductId(),
                    "teamId", null == createPayRequestDTO ? null : createPayRequestDTO.getTeamId(),
                    "activityId", null == createPayRequestDTO ? null : createPayRequestDTO.getActivityId(),
                    "marketType", null == createPayRequestDTO ? null : createPayRequestDTO.getMarketType(),
                    "payChannel", null == createPayRequestDTO ? null : createPayRequestDTO.getPayChannel(),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return Response.<String>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "group_buy_notify", method = RequestMethod.POST)
    @Override
    public String groupBuyNotify(@RequestBody NotifyRequestDTO requestDTO) {
        long startMillis = System.currentTimeMillis();
        log.info("拼团回调，组队完成，结算开始 {}", JSON.toJSONString(requestDTO));
        try {
            if (null == requestDTO || null == requestDTO.getOutTradeNoList()) {
                businessLogger.warn("mall_group_buy_notify", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return "error";
            }
            // 营销结算
            orderService.changeOrderMarketSettlement(requestDTO.getOutTradeNoList());
            businessLogger.info("mall_group_buy_notify", "success", businessLogger.fields(
                    "teamId", requestDTO.getTeamId(),
                    "outTradeNoCount", null == requestDTO.getOutTradeNoList() ? 0 : requestDTO.getOutTradeNoList().size(),
                    "costMs", System.currentTimeMillis() - startMillis));
            return "success";
        } catch (Exception e) {
            log.error("拼团回调，组队完成，结算失败 {}", JSON.toJSONString(requestDTO), e);
            businessLogger.error("mall_group_buy_notify", "system_error", businessLogger.fields(
                    "teamId", null == requestDTO ? null : requestDTO.getTeamId(),
                    "outTradeNoCount", null == requestDTO || null == requestDTO.getOutTradeNoList() ? 0 : requestDTO.getOutTradeNoList().size(),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return "error";
        }
    }

    /**
     * http://xfg-studio.natapp1.cc/api/v1/alipay/alipay_notify_url
     */
    @RequestMapping(value = "alipay_notify_url", method = RequestMethod.POST)
    public String payNotify(HttpServletRequest request) {
        long startMillis = System.currentTimeMillis();
        log.info("支付回调，消息接收 {}", request.getParameter("trade_status"));
        try {
            return alipayNotifySupport.handle(request, startMillis);
        } catch (Exception e) {
            log.error("支付回调处理失败 outTradeNo:{}", request.getParameter("out_trade_no"), e);
            businessLogger.error("mall_pay_notify", "system_error", businessLogger.fields(
                    "tradeStatus", request.getParameter("trade_status"),
                    "outTradeNo", request.getParameter("out_trade_no"),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            return "false";
        }
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
        try {
            log.info("查询用户订单列表开始 userId:{} lastId:{} pageSize:{}", requestDTO.getUserId(), requestDTO.getLastId(), requestDTO.getPageSize());
            
            String userId = requestDTO.getUserId();
            Long lastId = requestDTO.getLastId();
            Integer pageSize = requestDTO.getPageSize();
            
            // 查询订单列表，多查询一条用于判断是否还有更多数据
            List<OrderEntity> orderList = orderService.queryUserOrderList(userId, lastId, pageSize + 1);
            
            // 判断是否还有更多数据
            boolean hasMore = orderList.size() > pageSize;
            if (hasMore) {
                orderList = orderList.subList(0, pageSize);
            }
            
            QueryOrderListResponseDTO responseDTO = orderListResponseAssembler.assemble(orderList, hasMore);
            
            log.info("查询用户订单列表完成 userId:{} 返回订单数量:{} hasMore:{}", userId, responseDTO.getOrderList().size(), hasMore);
            return Response.<QueryOrderListResponseDTO>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("查询用户订单列表失败 userId:{}", requestDTO.getUserId(), e);
            return Response.<QueryOrderListResponseDTO>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
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
        long startMillis = System.currentTimeMillis();
        try {
            if (null == requestDTO) {
                businessLogger.warn("mall_refund_order", "illegal_parameter", businessLogger.fields(
                        "costMs", System.currentTimeMillis() - startMillis));
                return Response.<RefundOrderResponseDTO>builder()
                        .code(Constants.ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(Constants.ResponseCode.ILLEGAL_PARAMETER.getInfo())
                        .build();
            }
            log.info("用户退单开始 userId:{} orderId:{}", requestDTO.getUserId(), requestDTO.getOrderId());
            
            String userId = requestDTO.getUserId();
            String orderId = requestDTO.getOrderId();
            
            // 执行退单操作
            boolean success = orderService.refundMarketOrder(userId, orderId);
            
            RefundOrderResponseDTO responseDTO = new RefundOrderResponseDTO();
            responseDTO.setSuccess(success);
            responseDTO.setOrderId(orderId);
            responseDTO.setMessage(success ? "退单成功" : "退单失败，订单不存在、已关闭或不属于该用户");
            
            log.info("用户退单完成 userId:{} orderId:{} success:{}", userId, orderId, success);
            businessLogger.info("mall_refund_order", success ? "success" : "rejected", businessLogger.fields(
                    "userId", userId,
                    "orderId", orderId,
                    "success", success,
                    "costMs", System.currentTimeMillis() - startMillis));
            return Response.<RefundOrderResponseDTO>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("用户退单失败 userId:{} orderId:{}", null == requestDTO ? null : requestDTO.getUserId(), null == requestDTO ? null : requestDTO.getOrderId(), e);
            businessLogger.error("mall_refund_order", "system_error", businessLogger.fields(
                    "userId", null == requestDTO ? null : requestDTO.getUserId(),
                    "orderId", null == requestDTO ? null : requestDTO.getOrderId(),
                    "costMs", System.currentTimeMillis() - startMillis), e);
            
            RefundOrderResponseDTO responseDTO = new RefundOrderResponseDTO();
            responseDTO.setSuccess(false);
            responseDTO.setOrderId(null == requestDTO ? null : requestDTO.getOrderId());
            responseDTO.setMessage("退单失败，系统异常");
            
            return Response.<RefundOrderResponseDTO>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info(Constants.ResponseCode.UN_ERROR.getInfo())
                    .data(responseDTO)
                    .build();
        }
    }

    /**
     * 测试回调接口 - 主动查询支付宝交易状态
     * @param outTradeNo 商户订单号
     * @return 处理结果
     */
    @RequestMapping(value = "active_pay_notify", method = RequestMethod.POST)
    public Response<String> activePayNotify(@RequestParam String outTradeNo) {
        return activePayNotifySupport.handle(outTradeNo, System.currentTimeMillis());
    }

}
