package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.seckill.adapter.port.ISeckillManualCompensationPort;
import cn.bugstack.domain.seckill.model.entity.SeckillManualMessageEntity;
import cn.bugstack.infrastructure.event.SeckillManualCompensationStream;
import cn.bugstack.infrastructure.event.SeckillOrderCreateBuffer;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class SeckillManualCompensationPort implements ISeckillManualCompensationPort {

    @Resource
    private SeckillManualCompensationStream seckillManualCompensationStream;

    @Resource
    private SeckillOrderCreateBuffer seckillOrderCreateBuffer;

    @Override
    public List<SeckillManualMessageEntity> queryManualMessages(int limit) {
        return seckillManualCompensationStream.queryMessages(limit);
    }

    @Override
    public int replayManualMessages(List<String> messageIds, int limit) {
        List<SeckillManualMessageEntity> messages = new ArrayList<>();
        if (null != messageIds && !messageIds.isEmpty()) {
            for (String messageId : messageIds) {
                SeckillManualMessageEntity deadMessage = seckillManualCompensationStream.queryMessage(messageId);
                if (null != deadMessage) {
                    messages.add(deadMessage);
                }
            }
        } else {
            messages.addAll(seckillManualCompensationStream.queryMessages(Math.max(1, limit)));
        }

        int count = 0;
        for (SeckillManualMessageEntity deadMessage : messages) {
            if (null == deadMessage.getBody() || deadMessage.getBody().trim().isEmpty()) {
                continue;
            }
            seckillOrderCreateBuffer.offer(deadMessage.getBody(), deadMessage.getBody());
            seckillManualCompensationStream.remove(deadMessage.getId());
            count++;
        }
        return count;
    }

    @Override
    public String manualStreamKey() {
        return seckillManualCompensationStream.streamKey();
    }

}
