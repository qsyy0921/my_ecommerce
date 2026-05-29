package cn.bugstack.infrastructure.redis;

import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Redis 服务 - Redisson
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 */
@Service("redissonService")
public class RedissonService implements IRedisService {

    @Resource
    private RedissonClient redissonClient;

    public <T> void setValue(String key, T value) {
        redissonClient.<T>getBucket(key).set(value);
    }

    @Override
    public <T> void setValue(String key, T value, long expired) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value, Duration.ofMillis(expired));
    }

    public <T> T getValue(String key) {
        return redissonClient.<T>getBucket(key).get();
    }

    @Override
    public <T> RQueue<T> getQueue(String key) {
        return redissonClient.getQueue(key);
    }

    @Override
    public <T> RBlockingQueue<T> getBlockingQueue(String key) {
        return redissonClient.getBlockingQueue(key);
    }

    @Override
    public <T> RDelayedQueue<T> getDelayedQueue(RBlockingQueue<T> rBlockingQueue) {
        return redissonClient.getDelayedQueue(rBlockingQueue);
    }

    @Override
    public void setAtomicLong(String key, long value) {
        redissonClient.getAtomicLong(key).set(value);
    }

    @Override
    public Long getAtomicLong(String key) {
        return redissonClient.getAtomicLong(key).get();
    }

    @Override
    public long incr(String key) {
        return redissonClient.getAtomicLong(key).incrementAndGet();
    }

    @Override
    public long incrBy(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(delta);
    }

    @Override
    public long decr(String key) {
        return redissonClient.getAtomicLong(key).decrementAndGet();
    }

    @Override
    public long decrBy(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(-delta);
    }

    @Override
    public Long reserveSeckillStock(String stockKey, String userKey, long userKeyTtl, TimeUnit timeUnit) {
        String luaScript =
                "if redis.call('exists', KEYS[2]) == 1 then " +
                "  return -2 " +
                "end " +
                "local stock = tonumber(redis.call('get', KEYS[1]) or '0') " +
                "if stock <= 0 then " +
                "  return -1 " +
                "end " +
                "redis.call('decr', KEYS[1]) " +
                "redis.call('psetex', KEYS[2], ARGV[1], '1') " +
                "return stock - 1";
        Number result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                luaScript,
                RScript.ReturnType.INTEGER,
                Arrays.<Object>asList(stockKey, userKey),
                String.valueOf(timeUnit.toMillis(userKeyTtl)));
        return result.longValue();
    }

    @Override
    public Long reserveSeckillQualification(String stockKey, String userKey, String resultKey, String processingResult, long ttl, TimeUnit timeUnit) {
        String luaScript =
                "if redis.call('exists', KEYS[2]) == 1 or redis.call('exists', KEYS[3]) == 1 then " +
                "  return -2 " +
                "end " +
                "local stock = tonumber(redis.call('get', KEYS[1]) or '0') " +
                "if stock <= 0 then " +
                "  return -1 " +
                "end " +
                "redis.call('decr', KEYS[1]) " +
                "redis.call('psetex', KEYS[2], ARGV[1], '1') " +
                "redis.call('psetex', KEYS[3], ARGV[1], ARGV[2]) " +
                "return stock - 1";
        Number result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                luaScript,
                RScript.ReturnType.INTEGER,
                Arrays.<Object>asList(stockKey, userKey, resultKey),
                String.valueOf(timeUnit.toMillis(ttl)),
                processingResult);
        return result.longValue();
    }

    @Override
    public Long reserveTeamStock(String teamStockKey, String recoveryTeamStockKey, String occupyLockKeyPrefix,
                                 String userOccupyKey, String outTradeNo, int target, long lockTtl, TimeUnit timeUnit) {
        String luaScript =
                "local userOutTradeNo = redis.call('get', KEYS[4]) " +
                "if userOutTradeNo then " +
                "  if userOutTradeNo == ARGV[3] then " +
                "    return -4 " +
                "  end " +
                "  return -3 " +
                "end " +
                "local recovery = tonumber(redis.call('get', KEYS[2]) or '0') " +
                "local occupy = redis.call('incr', KEYS[1]) + 1 " +
                "if occupy > tonumber(ARGV[1]) + recovery then " +
                "  redis.call('decr', KEYS[1]) " +
                "  return -1 " +
                "end " +
                "local lockKey = KEYS[3] .. occupy " +
                "local locked = redis.call('set', lockKey, '1', 'PX', ARGV[2], 'NX') " +
                "if not locked then " +
                "  redis.call('decr', KEYS[1]) " +
                "  return -2 " +
                "end " +
                "local userLocked = redis.call('set', KEYS[4], ARGV[3], 'PX', ARGV[2], 'NX') " +
                "if not userLocked then " +
                "  redis.call('del', lockKey) " +
                "  redis.call('decr', KEYS[1]) " +
                "  return -3 " +
                "end " +
                "return occupy";
        Number result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                luaScript,
                RScript.ReturnType.INTEGER,
                Arrays.<Object>asList(teamStockKey, recoveryTeamStockKey, occupyLockKeyPrefix, userOccupyKey),
                String.valueOf(target),
                String.valueOf(timeUnit.toMillis(lockTtl)),
                outTradeNo);
        return result.longValue();
    }

    @Override
    public void remove(String key) {
        redissonClient.getBucket(key).delete();
    }

    @Override
    public boolean isExists(String key) {
        return redissonClient.getBucket(key).isExists();
    }

    public void addToSet(String key, String value) {
        RSet<String> set = redissonClient.getSet(key);
        set.add(value);
    }

    public boolean isSetMember(String key, String value) {
        RSet<String> set = redissonClient.getSet(key);
        return set.contains(value);
    }

    public void addToList(String key, String value) {
        RList<String> list = redissonClient.getList(key);
        list.add(value);
    }

    public String getFromList(String key, int index) {
        RList<String> list = redissonClient.getList(key);
        return list.get(index);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String key) {
        return redissonClient.getMap(key);
    }

    public void addToMap(String key, String field, String value) {
        RMap<String, String> map = redissonClient.getMap(key);
        map.put(field, value);
    }

    public String getFromMap(String key, String field) {
        RMap<String, String> map = redissonClient.getMap(key);
        return map.get(field);
    }

    @Override
    public <K, V> V getFromMap(String key, K field) {
        return redissonClient.<K, V>getMap(key).get(field);
    }

    public void addToSortedSet(String key, String value) {
        RSortedSet<String> sortedSet = redissonClient.getSortedSet(key);
        sortedSet.add(value);
    }

    @Override
    public RLock getLock(String key) {
        return redissonClient.getLock(key);
    }

    @Override
    public RLock getFairLock(String key) {
        return redissonClient.getFairLock(key);
    }

    @Override
    public RReadWriteLock getReadWriteLock(String key) {
        return redissonClient.getReadWriteLock(key);
    }

    @Override
    public RSemaphore getSemaphore(String key) {
        return redissonClient.getSemaphore(key);
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(String key) {
        return redissonClient.getPermitExpirableSemaphore(key);
    }

    @Override
    public RCountDownLatch getCountDownLatch(String key) {
        return redissonClient.getCountDownLatch(key);
    }

    @Override
    public <T> RBloomFilter<T> getBloomFilter(String key) {
        return redissonClient.getBloomFilter(key);
    }

    @Override
    public Boolean setNx(String key) {
        return redissonClient.getBucket(key).trySet("lock");
    }

    @Override
    public Boolean setNx(String key, long expired, TimeUnit timeUnit) {
        return redissonClient.getBucket(key).trySet("lock", expired, timeUnit);
    }

    @Override
    public RBitSet getBitSet(String key) {
        return redissonClient.getBitSet(key);
    }

}
