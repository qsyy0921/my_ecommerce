package cn.bugstack.domain.trade.service.refund.business;

import cn.bugstack.domain.shared.adapter.port.IDomainTaskExecutor;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyRefundPort;
import cn.bugstack.domain.trade.adapter.port.IGroupBuyTeamStockPort;
import cn.bugstack.domain.trade.model.entity.NotifyTaskEntity;
import cn.bugstack.domain.trade.model.valobj.TeamRefundSuccess;
import cn.bugstack.domain.trade.service.ITradeTaskService;
import cn.bugstack.domain.trade.service.lock.factory.TradeLockRuleFilterFactory;
import cn.bugstack.types.exception.AppException;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 退单策略抽象基类
 * 提供共用的依赖注入和MQ消息发送功能
 *
 * @author qsyy
 * @create 2025-01-01 00:00
 */
@Slf4j
public abstract class AbstractRefundOrderStrategy implements IRefundOrderStrategy {

    protected final IGroupBuyRefundPort groupBuyRefundPort;
    protected final IGroupBuyTeamStockPort groupBuyTeamStockPort;
    protected final ITradeTaskService tradeTaskService;
    protected final IDomainTaskExecutor domainTaskExecutor;

    protected AbstractRefundOrderStrategy(IGroupBuyRefundPort groupBuyRefundPort,
                                          IGroupBuyTeamStockPort groupBuyTeamStockPort,
                                          ITradeTaskService tradeTaskService,
                                          IDomainTaskExecutor domainTaskExecutor) {
        this.groupBuyRefundPort = groupBuyRefundPort;
        this.groupBuyTeamStockPort = groupBuyTeamStockPort;
        this.tradeTaskService = tradeTaskService;
        this.domainTaskExecutor = domainTaskExecutor;
    }

    /**
     * 异步发送MQ消息
     * @param notifyTaskEntity 通知任务实体
     * @param refundType 退单类型描述
     */
    protected void sendRefundNotifyMessage(NotifyTaskEntity notifyTaskEntity, String refundType) {
        if (null != notifyTaskEntity) {
            domainTaskExecutor.execute(() -> {
                Map<String, Integer> notifyResultMap = null;
                try {
                    notifyResultMap = tradeTaskService.execNotifyJob(notifyTaskEntity);
                    log.info("回调通知交易退单({}) result:{}", refundType, JSON.toJSONString(notifyResultMap));
                } catch (Exception e) {
                    log.error("回调通知交易退单失败({}) result:{}", refundType, JSON.toJSONString(notifyResultMap), e);
                    throw new AppException(e.getMessage());
                }
            });
        }
    }

    /**
     * 通用库存恢复逻辑
     * @param teamRefundSuccess 团队退单成功信息
     * @param refundType 退单类型描述
     * @throws Exception 异常
     */
    protected void doReverseStock(TeamRefundSuccess teamRefundSuccess, String refundType) throws Exception {
        log.info("退单；恢复锁单量 - {} {} {} {}", refundType, teamRefundSuccess.getUserId(), teamRefundSuccess.getActivityId(), teamRefundSuccess.getTeamId());
        // 1. 恢复库存key
        String recoveryTeamStockKey = TradeLockRuleFilterFactory.generateRecoveryTeamStockKey(teamRefundSuccess.getActivityId(), teamRefundSuccess.getTeamId());
        // 2. 退单恢复库存
        groupBuyTeamStockPort.refund2AddRecovery(recoveryTeamStockKey, teamRefundSuccess.getOrderId());
    }

}
