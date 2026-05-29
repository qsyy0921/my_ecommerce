package cn.bugstack.domain.trade.service.lock;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.aggregate.GroupBuyOrderAggregate;
import cn.bugstack.domain.trade.model.entity.*;
import cn.bugstack.domain.trade.model.valobj.GroupBuyProgressVO;
import cn.bugstack.domain.trade.service.ITradeLockOrderService;
import cn.bugstack.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.bugstack.types.enums.ResponseCode;
import cn.bugstack.types.exception.AppException;
import cn.bugstack.wrench.design.framework.link.model2.chain.BusinessLinkedList;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 交易订单服务
 * @create 2025-01-11 08:07
 */
@Slf4j
public class TradeLockOrderService implements ITradeLockOrderService {

    private final ITradeRepository repository;
    private final BusinessLinkedList<TradeLockRuleCommandEntity, TradeLockRuleFilterFactory.DynamicContext, TradeLockRuleFilterBackEntity> tradeRuleFilter;

    public TradeLockOrderService(ITradeRepository repository,
                                 BusinessLinkedList<TradeLockRuleCommandEntity, TradeLockRuleFilterFactory.DynamicContext, TradeLockRuleFilterBackEntity> tradeRuleFilter) {
        this.repository = repository;
        this.tradeRuleFilter = tradeRuleFilter;
    }

    @Override
    public MarketPayOrderEntity queryNoPayMarketPayOrderByOutTradeNo(String userId, String outTradeNo) {
        log.info("拼团交易-查询未支付营销订单:{} outTradeNo:{}", userId, outTradeNo);
        return repository.queryLockMarketPayOrderEntityByOutTradeNo(userId, outTradeNo);
    }

    @Override
    public GroupBuyProgressVO queryGroupBuyProgress(String teamId) {
        log.info("拼团交易-查询拼单进度:{}", teamId);
        return repository.queryGroupBuyProgress(teamId);
    }

    @Override
    public MarketPayOrderEntity lockMarketPayOrder(UserEntity userEntity, PayActivityEntity payActivityEntity, PayDiscountEntity payDiscountEntity) throws Exception {
        log.info("拼团交易-锁定营销优惠支付订单:{} activityId:{} goodsId:{}", userEntity.getUserId(), payActivityEntity.getActivityId(), payDiscountEntity.getGoodsId());

        boolean requestLock = repository.tryAcquireLockRequest(userEntity.getUserId(), payDiscountEntity.getOutTradeNo(), payActivityEntity.getValidTime());
        if (!requestLock) {
            MarketPayOrderEntity marketPayOrderEntity = waitLockResult(userEntity.getUserId(), payDiscountEntity.getOutTradeNo());
            if (null != marketPayOrderEntity) {
                return marketPayOrderEntity;
            }
            throw new AppException(ResponseCode.E0010);
        }

        TradeLockRuleFilterBackEntity tradeLockRuleFilterBackEntity = null;
        try {
            // 交易规则过滤
            tradeLockRuleFilterBackEntity = tradeRuleFilter.apply(TradeLockRuleCommandEntity.builder()
                            .activityId(payActivityEntity.getActivityId())
                            .userId(userEntity.getUserId())
                            .teamId(payActivityEntity.getTeamId())
                            .outTradeNo(payDiscountEntity.getOutTradeNo())
                            .build(),
                    new TradeLockRuleFilterFactory.DynamicContext());

            // 已参与拼团量 - 用于构建数据库唯一索引使用，确保用户只能在一个活动上参与固定的次数
            Integer userTakeOrderCount = tradeLockRuleFilterBackEntity.getUserTakeOrderCount();

            // 构建聚合对象
            GroupBuyOrderAggregate groupBuyOrderAggregate = GroupBuyOrderAggregate.builder()
                    .userEntity(userEntity)
                    .payActivityEntity(payActivityEntity)
                    .payDiscountEntity(payDiscountEntity)
                    .userTakeOrderCount(userTakeOrderCount)
                    .build();

            // 锁定聚合订单 - 这会用户只是下单还没有支付。后续会有2个流程；支付成功、超时未支付（回退）
            MarketPayOrderEntity marketPayOrderEntity = repository.lockMarketPayOrder(groupBuyOrderAggregate);
            repository.cacheLockResult(userEntity.getUserId(), payDiscountEntity.getOutTradeNo(), marketPayOrderEntity, payActivityEntity.getValidTime());
            return marketPayOrderEntity;
        } catch (Exception e) {
            // 记录失败恢复量
            if (null != tradeLockRuleFilterBackEntity) {
                repository.recoveryTeamStock(tradeLockRuleFilterBackEntity.getRecoveryTeamStockKey(), payActivityEntity.getValidTime());
                repository.releaseUserTeamOccupy(tradeLockRuleFilterBackEntity.getUserTeamOccupyKey());
            }
            throw e;
        } finally {
            repository.releaseLockRequest(userEntity.getUserId(), payDiscountEntity.getOutTradeNo());
        }

    }

    private MarketPayOrderEntity waitLockResult(String userId, String outTradeNo) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(50L);
            MarketPayOrderEntity marketPayOrderEntity = repository.queryLockMarketPayOrderEntityByOutTradeNo(userId, outTradeNo);
            if (null != marketPayOrderEntity) {
                return marketPayOrderEntity;
            }
        }
        return null;
    }

}
