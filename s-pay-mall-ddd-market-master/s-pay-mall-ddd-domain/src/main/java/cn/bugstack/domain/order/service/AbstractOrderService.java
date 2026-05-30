package cn.bugstack.domain.order.service;

import cn.bugstack.domain.order.adapter.port.IMarketOrderLockPort;
import cn.bugstack.domain.order.adapter.port.IProductQueryPort;
import cn.bugstack.domain.order.adapter.repository.IOrderRepository;
import cn.bugstack.domain.order.model.aggregate.CreateOrderAggregate;
import cn.bugstack.domain.order.model.entity.*;
import cn.bugstack.domain.order.model.valobj.MarketTypeVO;
import cn.bugstack.domain.order.model.valobj.OrderStatusVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
public abstract class AbstractOrderService implements IOrderService {

    protected final IOrderRepository repository;

    protected final IProductQueryPort productQueryPort;
    protected final IMarketOrderLockPort marketOrderLockPort;

    public AbstractOrderService(IOrderRepository repository,
                                IProductQueryPort productQueryPort,
                                IMarketOrderLockPort marketOrderLockPort) {
        this.repository = repository;
        this.productQueryPort = productQueryPort;
        this.marketOrderLockPort = marketOrderLockPort;
    }

    @Override
    public PayOrderEntity createOrder(ShopCartEntity shopCartEntity) throws Exception {
        // 1. 查询当前用户是否存在掉单和未支付订单
        OrderEntity unpaidOrderEntity = repository.queryUnPayOrder(shopCartEntity);

        if (null != unpaidOrderEntity && OrderStatusVO.PAY_WAIT.equals(unpaidOrderEntity.getOrderStatusVO())) {
            log.info("创建订单-存在，已存在未支付订单。userId:{} productId:{} orderId:{}", shopCartEntity.getUserId(), shopCartEntity.getProductId(), unpaidOrderEntity.getOrderId());
            return PayOrderEntity.builder()
                    .orderId(unpaidOrderEntity.getOrderId())
                    .payUrl(unpaidOrderEntity.getPayUrl())
                    .build();
        } else if (null != unpaidOrderEntity && OrderStatusVO.CREATE.equals(unpaidOrderEntity.getOrderStatusVO())) {
            log.info("创建订单-存在，存在未创建支付单订单，创建支付单开始 userId:{} productId:{} orderId:{}", shopCartEntity.getUserId(), shopCartEntity.getProductId(), unpaidOrderEntity.getOrderId());
            Integer marketType = unpaidOrderEntity.getMarketType();
            BigDecimal marketDeductionAmount = unpaidOrderEntity.getMarketDeductionAmount();

            PayOrderEntity payOrderEntity = null;

            if (isMarketOrder(marketType) && hasNoMarketDiscount(marketDeductionAmount)) {
                MarketPayDiscountEntity marketPayDiscountEntity = this.lockPayMarketOrder(shopCartEntity,
                        unpaidOrderEntity.getOrderId());

                payOrderEntity = doPrepayOrder(shopCartEntity.getUserId(), shopCartEntity.getProductId(),
                        unpaidOrderEntity.getProductName(), unpaidOrderEntity.getOrderId(), unpaidOrderEntity.getTotalAmount(), marketPayDiscountEntity, shopCartEntity.getPayChannel());
            } else if (isMarketOrder(marketType)) {
                payOrderEntity = doPrepayOrder(shopCartEntity.getUserId(), shopCartEntity.getProductId(),
                        unpaidOrderEntity.getProductName(), unpaidOrderEntity.getOrderId(), unpaidOrderEntity.getPayAmount(), shopCartEntity.getPayChannel());
            } else {
                payOrderEntity = doPrepayOrder(shopCartEntity.getUserId(), shopCartEntity.getProductId(),
                        unpaidOrderEntity.getProductName(), unpaidOrderEntity.getOrderId(), unpaidOrderEntity.getTotalAmount(), shopCartEntity.getPayChannel());
            }

            return PayOrderEntity.builder()
                    .orderId(payOrderEntity.getOrderId())
                    .payUrl(payOrderEntity.getPayUrl())
                    .build();
        }

        // 查询商品信息
        ProductEntity productEntity = productQueryPort.queryProductByProductId(shopCartEntity.getProductId());

        // 订单实体信息
        OrderEntity orderEntity = CreateOrderAggregate.buildOrderEntity(productEntity.getProductId(), productEntity.getProductName(), shopCartEntity.getMarketTypeVO().getCode());

        // 订单聚合对象
        CreateOrderAggregate orderAggregate = CreateOrderAggregate.builder()
                .userId(shopCartEntity.getUserId())
                .productEntity(productEntity)
                .orderEntity(orderEntity)
                .build();

        // 创建本地订单
        this.doSaveOrder(orderAggregate);

        // 发起营销锁单
        MarketPayDiscountEntity marketPayDiscountEntity = null;
        if (isMarketOrder(shopCartEntity.getMarketTypeVO().getCode())) {
            marketPayDiscountEntity = this.lockPayMarketOrder(shopCartEntity, orderEntity.getOrderId());
        }

        // 创建支付订单
        PayOrderEntity payOrderEntity = doPrepayOrder(shopCartEntity.getUserId(),
                productEntity.getProductId(),
                productEntity.getProductName(),
                orderEntity.getOrderId(),
                productEntity.getPrice(),
                marketPayDiscountEntity,
                shopCartEntity.getPayChannel());

        log.info("创建订单-完成，生成支付单。userId: {} orderId: {} payUrl: {}", shopCartEntity.getUserId(), orderEntity.getOrderId(), payOrderEntity.getPayUrl());

        return PayOrderEntity.builder()
                .orderId(orderEntity.getOrderId())
                .payUrl(payOrderEntity.getPayUrl())
                .build();
    }

    protected abstract void doSaveOrder(CreateOrderAggregate orderAggregate);

    protected abstract PayOrderEntity doPrepayOrder(String userId, String productId, String productName, String orderId, BigDecimal totalAmount, String payChannel);

    protected abstract PayOrderEntity doPrepayOrder(String userId, String productId, String productName, String orderId, BigDecimal totalAmount, MarketPayDiscountEntity marketPayDiscountEntity, String payChannel);

    private MarketPayDiscountEntity lockPayMarketOrder(ShopCartEntity shopCartEntity, String orderId) {
        MarketPayDiscountEntity marketPayDiscountEntity = null;
        if (MarketTypeVO.GROUP_BUY_MARKET.equals(shopCartEntity.getMarketTypeVO())) {
            marketPayDiscountEntity = marketOrderLockPort.lockGroupBuyMarketPayOrder(shopCartEntity.getUserId(),
                    shopCartEntity.getTeamId(),
                    shopCartEntity.getActivityId(),
                    shopCartEntity.getProductId(),
                    orderId);
        } else if (MarketTypeVO.SECKILL_MARKET.equals(shopCartEntity.getMarketTypeVO())) {
            marketPayDiscountEntity = marketOrderLockPort.lockSeckillPayOrder(shopCartEntity.getUserId(),
                    shopCartEntity.getActivityId(),
                    shopCartEntity.getProductId(),
                    orderId);
        }
        if (null == marketPayDiscountEntity) {
            throw new RuntimeException("market lock failed");
        }
        return marketPayDiscountEntity;
    }

    private boolean isMarketOrder(Integer marketType) {
        return MarketTypeVO.GROUP_BUY_MARKET.getCode().equals(marketType)
                || MarketTypeVO.SECKILL_MARKET.getCode().equals(marketType);
    }

    private boolean hasNoMarketDiscount(BigDecimal marketDeductionAmount) {
        return null == marketDeductionAmount || BigDecimal.ZERO.compareTo(marketDeductionAmount) == 0;
    }

    @Override
    public List<OrderEntity> queryUserOrderList(String userId, Long lastId, Integer pageSize) {
        return repository.queryUserOrderList(userId, lastId, pageSize);
    }

}
