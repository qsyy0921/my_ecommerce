package cn.bugstack.domain.order.service;

import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.PayOrderEntity;
import cn.bugstack.domain.order.model.entity.ReconcileCaseEntity;
import cn.bugstack.domain.order.model.entity.ShopCartEntity;

import java.util.Date;
import java.util.List;

public interface IOrderService {

    PayOrderEntity createOrder(ShopCartEntity shopCartEntity) throws Exception;

    void changeOrderPaySuccess(String orderId, Date orderTime);

    void changeOrderPaySuccess(String orderId, Date orderTime, String payChannel, String channelTradeNo, String rawMessage);

    List<String> queryNoPayNotifyOrder();

    List<String> queryTimeoutCloseOrderList();

    boolean changeOrderClose(String orderId);

    void changeOrderMarketSettlement(List<String> outTradeNoList);

    List<OrderEntity> queryUserOrderList(String userId, Long lastId, Integer pageSize);

    /**
     * 营销退单
     */
    boolean refundMarketOrder(String userId, String orderId);

    /**
     * 接收拼团退单消息
     */
    boolean refundPayOrder(String userId, String orderId);

    int reconcileMarketSettlementOrders();

    int scanReconcileCases();

    List<ReconcileCaseEntity> queryReconcileCaseList(Integer caseStatus, String caseType, Long lastId, Integer pageSize);

    boolean handleReconcileCase(String caseNo, Integer caseStatus, String handler, String handleNote);

    boolean replayReconcileCase(String caseNo, String operator);

    void recordReconcileOperation(String operator, String operationType, String bizId, String requestBody, String result);

    int importThirdPartyBillCsv(String csvText);

}
