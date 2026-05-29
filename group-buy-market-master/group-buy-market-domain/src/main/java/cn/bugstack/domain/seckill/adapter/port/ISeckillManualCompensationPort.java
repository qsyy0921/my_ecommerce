package cn.bugstack.domain.seckill.adapter.port;

import cn.bugstack.domain.seckill.model.entity.SeckillManualMessageEntity;

import java.util.List;

/**
 * Port for operating seckill messages that have been isolated for manual compensation.
 */
public interface ISeckillManualCompensationPort {

    List<SeckillManualMessageEntity> queryManualMessages(int limit);

    int replayManualMessages(List<String> messageIds, int limit);

    String manualStreamKey();

}
