package cn.bugstack.domain.activity.service.trial.node;

import cn.bugstack.domain.activity.adapter.port.IActivityTrialQueryPort;
import cn.bugstack.domain.activity.model.entity.MarketProductEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.SCSkuActivityVO;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.domain.activity.service.discount.IDiscountCalculateService;
import cn.bugstack.domain.activity.service.trial.factory.DefaultActivityStrategyFactory;
import cn.bugstack.domain.shared.adapter.port.IDomainTaskExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * @author qsyy
 * @description 线程案例举例
 * @create 2025-04-03 07:44
 */
@Slf4j
public class MarketNode2CompletableFuture extends MarketNode {

    public MarketNode2CompletableFuture(IActivityTrialQueryPort activityTrialQueryPort,
                                        IDomainTaskExecutor domainTaskExecutor,
                                        Map<String, IDiscountCalculateService> discountCalculateServiceMap,
                                        ErrorNode errorNode,
                                        TagNode tagNode) {
        super(activityTrialQueryPort, domainTaskExecutor, discountCalculateServiceMap, errorNode, tagNode);
    }

    @Override
    protected void multiThread(MarketProductEntity requestParameter, DefaultActivityStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // 异步查询活动配置
        CompletableFuture<GroupBuyActivityDiscountVO> groupBuyActivityDiscountVOCompletableFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Long availableActivityId = requestParameter.getActivityId();
                if (null == requestParameter.getActivityId()) {
                    // 查询渠道商品活动配置关联配置
                    SCSkuActivityVO scSkuActivityVO = activityTrialQueryPort.querySCSkuActivityBySCGoodsId(requestParameter.getSource(), requestParameter.getChannel(), requestParameter.getGoodsId());
                    if (null == scSkuActivityVO) return null;
                    availableActivityId = scSkuActivityVO.getActivityId();
                }
                // 查询活动配置
                return activityTrialQueryPort.queryGroupBuyActivityDiscountVO(availableActivityId);
            } catch (Exception e) {
                log.error("异步查询活动配置异常", e);
                return null;
            }
        }, domainTaskExecutor::execute);

        // 异步查询商品信息 - 在实际生产中，商品有同步库或者调用接口查询。这里暂时使用DB方式查询。
        CompletableFuture<SkuVO> skuVOCompletableFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return activityTrialQueryPort.querySkuByGoodsId(requestParameter.getGoodsId());
            } catch (Exception e) {
                log.error("异步查询商品信息异常", e);
                return null;
            }
        }, domainTaskExecutor::execute);

        // 等待所有异步任务完成并写入上下文
        CompletableFuture.allOf(groupBuyActivityDiscountVOCompletableFuture, skuVOCompletableFuture)
                .thenRun(() -> {
                    dynamicContext.setGroupBuyActivityDiscountVO(groupBuyActivityDiscountVOCompletableFuture.join());
                    dynamicContext.setSkuVO(skuVOCompletableFuture.join());
                }).join();

        log.info("拼团商品查询试算服务-MarketNode userId:{} 异步线程加载数据「GroupBuyActivityDiscountVO、SkuVO」完成", requestParameter.getUserId());
    }
}
