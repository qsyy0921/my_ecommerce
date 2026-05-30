package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.order.adapter.repository.IOrderRepository;
import cn.bugstack.domain.order.model.aggregate.CreateOrderAggregate;
import cn.bugstack.domain.order.model.entity.OrderEntity;
import cn.bugstack.domain.order.model.entity.PayOrderEntity;
import cn.bugstack.domain.order.model.entity.ShopCartEntity;
import cn.bugstack.infrastructure.adapter.support.PayOrderEntityMapper;
import cn.bugstack.infrastructure.dao.IOrderDao;
import cn.bugstack.infrastructure.dao.po.PayOrder;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Repository
public class OrderRepository implements IOrderRepository {

    @Resource
    private IOrderDao orderDao;
    private final PayOrderEntityMapper payOrderEntityMapper = new PayOrderEntityMapper();

    @Override
    public void doSaveOrder(CreateOrderAggregate orderAggregate) {
        orderDao.insert(payOrderEntityMapper.toPayOrder(orderAggregate));
    }

    @Override
    public OrderEntity queryUnPayOrder(ShopCartEntity shopCartEntity) {
        return payOrderEntityMapper.toOrderEntity(orderDao.queryUnPayOrder(payOrderEntityMapper.toUnpaidQuery(shopCartEntity)));
    }

    @Override
    public void updateOrderPayInfo(PayOrderEntity payOrderEntity) {
        orderDao.updateOrderPayInfo(payOrderEntityMapper.toPayInfo(payOrderEntity));
    }

    @Override
    public boolean changeOrderPaySuccess(String orderId, Date payTime) {
        return 1 == orderDao.changeOrderPaySuccess(payOrderEntityMapper.toPaySuccessRequest(orderId, payTime));
    }

    @Override
    public boolean changeMarketOrderPaySuccess(String orderId) {
        return changeMarketOrderPaySuccess(orderId, new Date());
    }

    @Override
    public boolean changeMarketOrderPaySuccess(String orderId, Date payTime) {
        return 1 == orderDao.changeOrderPaySuccess(payOrderEntityMapper.toPaySuccessRequest(orderId, payTime));
    }

    @Override
    public List<String> queryNoPayNotifyOrder() {
        return orderDao.queryNoPayNotifyOrder();
    }

    @Override
    public List<String> queryTimeoutCloseOrderList() {
        return orderDao.queryTimeoutCloseOrderList();
    }

    @Override
    public boolean changeOrderClose(String orderId) {
        return orderDao.changeOrderClose(orderId);
    }

    @Override
    public void changeOrderMarketSettlement(List<String> outTradeNoList) {
        orderDao.changeOrderMarketSettlement(outTradeNoList);
    }

    @Override
    public OrderEntity queryOrderByOrderId(String orderId) {
        return payOrderEntityMapper.toOrderEntity(orderDao.queryOrderByOrderId(orderId));
    }

    @Override
    public List<OrderEntity> queryUserOrderList(String userId, Long lastId, Integer pageSize) {
        return payOrderEntityMapper.toOrderEntities(orderDao.queryUserOrderList(userId, lastId, pageSize));
    }

    @Override
    public OrderEntity queryOrderByUserIdAndOrderId(String userId, String orderId) {
        return payOrderEntityMapper.toOrderEntity(orderDao.queryOrderByUserIdAndOrderId(userId, orderId));
    }

    @Override
    public boolean refundOrder(String userId, String orderId) {
        return orderDao.refundOrder(userId, orderId);
    }

    @Override
    public boolean refundMarketOrder(String userId, String orderId) {
        return orderDao.refundMarketOrder(userId, orderId);
    }

}
