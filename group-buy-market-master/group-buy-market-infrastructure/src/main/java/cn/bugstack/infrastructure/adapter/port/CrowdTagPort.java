package cn.bugstack.infrastructure.adapter.port;

import cn.bugstack.domain.activity.adapter.port.ICrowdTagPort;
import cn.bugstack.infrastructure.redis.IRedisService;
import org.redisson.api.RBitSet;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;

@Repository
public class CrowdTagPort implements ICrowdTagPort {

    @Resource
    private IRedisService redisService;

    @Override
    public boolean isTagCrowdRange(String tagId, String userId) {
        RBitSet bitSet = redisService.getBitSet(tagId);
        if (!bitSet.isExists()) return true;
        return bitSet.get(redisService.getIndexFromUserId(userId));
    }

}
