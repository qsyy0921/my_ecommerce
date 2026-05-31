package cn.bugstack.domain.activity.service.trial.thread;

import cn.bugstack.domain.activity.adapter.port.IActivityTrialQueryPort;
import cn.bugstack.domain.activity.model.valobj.SkuVO;

import java.util.concurrent.Callable;

/**
 * @author qsyy
 * @description 查询商品信息任务
 * @create 2024-12-21 10:51
 */
public class QuerySkuVOFromDBThreadTask implements Callable<SkuVO> {

    private final String goodsId;

    private final IActivityTrialQueryPort activityTrialQueryPort;

    public QuerySkuVOFromDBThreadTask(String goodsId, IActivityTrialQueryPort activityTrialQueryPort) {
        this.goodsId = goodsId;
        this.activityTrialQueryPort = activityTrialQueryPort;
    }

    @Override
    public SkuVO call() throws Exception {
        return activityTrialQueryPort.querySkuByGoodsId(goodsId);
    }

}
