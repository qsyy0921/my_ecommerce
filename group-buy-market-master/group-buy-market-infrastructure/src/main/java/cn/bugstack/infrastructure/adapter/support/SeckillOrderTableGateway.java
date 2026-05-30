package cn.bugstack.infrastructure.adapter.support;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.infrastructure.dao.ISeckillOrderDao;
import cn.bugstack.infrastructure.dao.po.SeckillOrder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Component
public class SeckillOrderTableGateway {

    @Resource
    private ISeckillOrderDao seckillOrderDao;
    @Resource
    private SeckillOrderShardRouter seckillOrderShardRouter;

    public SeckillOrderEntity queryEntityByOutTradeNo(String userId, String outTradeNo) {
        return SeckillOrderAssembler.toEntity(queryByOutTradeNo(userId, outTradeNo));
    }

    public SeckillOrder queryByOutTradeNo(String userId, String outTradeNo) {
        SeckillOrder seckillOrderReq = SeckillOrder.builder()
                .userId(userId)
                .outTradeNo(outTradeNo)
                .build();
        if (seckillOrderShardRouter.useSharding()) {
            return seckillOrderDao.querySeckillOrderByOutTradeNoFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), seckillOrderReq);
        }
        return seckillOrderDao.querySeckillOrderByOutTradeNo(seckillOrderReq);
    }

    public void insert(SeckillOrder seckillOrder) {
        if (seckillOrderShardRouter.useSharding()) {
            seckillOrderDao.insertToTable(seckillOrderShardRouter.tableName(seckillOrder.getUserId(), seckillOrder.getOutTradeNo()), seckillOrder);
            return;
        }
        seckillOrderDao.insert(seckillOrder);
    }

    public void insertIgnoreBatch(List<SeckillOrder> seckillOrders) {
        if (!seckillOrderShardRouter.useSharding()) {
            seckillOrderDao.insertIgnoreBatch(seckillOrders);
            return;
        }

        Map<String, List<SeckillOrder>> orderMap = seckillOrderShardRouter.groupByTable(seckillOrders);
        for (Map.Entry<String, List<SeckillOrder>> entry : orderMap.entrySet()) {
            seckillOrderDao.insertIgnoreShardBatch(entry.getKey(), entry.getValue());
        }
    }

    public int paySuccess(String userId, String outTradeNo, String orderId) {
        return seckillOrderShardRouter.useSharding()
                ? seckillOrderDao.paySuccessOrderFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), orderId)
                : seckillOrderDao.paySuccessOrder(orderId);
    }

    public int closeUnpaid(String userId, String outTradeNo, String orderId) {
        return seckillOrderShardRouter.useSharding()
                ? seckillOrderDao.closeUnpaidOrderFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), orderId)
                : seckillOrderDao.closeUnpaidOrder(orderId);
    }

    public int refundPaid(String userId, String outTradeNo, String orderId) {
        return seckillOrderShardRouter.useSharding()
                ? seckillOrderDao.refundPaidOrderFromTable(seckillOrderShardRouter.tableName(userId, outTradeNo), orderId)
                : seckillOrderDao.refundPaidOrder(orderId);
    }

}
