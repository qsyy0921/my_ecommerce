package cn.bugstack.infrastructure.event;

import cn.bugstack.domain.seckill.model.entity.SeckillOrderEntity;
import cn.bugstack.domain.seckill.service.ISeckillService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 秒杀缓冲队列消费者，把入口抢到的资格异步转换成真实订单。
 */
@Slf4j
@Component
public class SeckillOrderCreateBufferWorker {

    @Value("${app.seckill.order-create-buffer.worker-count:16}")
    private Integer workerCount;

    @Value("${app.seckill.order-create-buffer.batch-size:100}")
    private Integer batchSize;

    @Resource
    private SeckillOrderCreateBuffer seckillOrderCreateBuffer;

    @Resource
    private ISeckillService seckillService;

    @Resource
    private SeckillStreamMetrics seckillStreamMetrics;

    @Resource
    private SeckillFaultInjector seckillFaultInjector;

    private volatile boolean running;
    private ExecutorService executorService;

    @PostConstruct
    public void start() {
        if (seckillOrderCreateBuffer.useMq()) {
            log.info("seckill order create buffer worker disabled, mode:mq");
            return;
        }

        running = true;
        int workers = Math.max(1, workerCount);
        executorService = Executors.newFixedThreadPool(workers, new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "seckill-order-buffer-worker-" + index.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });

        for (int i = 0; i < workers; i++) {
            String consumerName = "consumer-" + i;
            executorService.execute(() -> consumeLoop(consumerName));
        }
        log.info("seckill order create buffer worker started mode:{} workers:{} batchSize:{}", seckillOrderCreateBuffer.mode(), workers, batchSize);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (null != executorService) {
            executorService.shutdownNow();
        }
    }

    private void consumeLoop(String consumerName) {
        while (running) {
            List<SeckillOrderCreateBuffer.BufferMessage> messages = null;
            long startNanos = 0L;
            try {
                messages = seckillOrderCreateBuffer.pollBatch(
                        consumerName,
                        batchSize,
                        1,
                        TimeUnit.SECONDS);
                if (messages.isEmpty()) {
                    continue;
                }
                startNanos = System.nanoTime();
                List<SeckillOrderEntity> orderEntities = new ArrayList<>(messages.size());
                for (SeckillOrderCreateBuffer.BufferMessage message : messages) {
                    SeckillOrderEntity orderEntity = JSON.parseObject(message.getBody(), SeckillOrderEntity.class);
                    orderEntity.setSourceMessageId(message.getStreamKey() + ":" + String.valueOf(message.getStreamMessageId()));
                    orderEntities.add(orderEntity);
                }
                seckillService.createSeckillOrders(orderEntities);
                seckillFaultInjector.beforeStreamAck();
                seckillOrderCreateBuffer.ack(messages);
                seckillStreamMetrics.recordConsumeBatch(System.nanoTime() - startNanos, messages.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (null != messages && !messages.isEmpty()) {
                    seckillOrderCreateBuffer.fail(messages, e);
                }
                if (startNanos > 0L && null != messages) {
                    seckillStreamMetrics.recordConsumeBatch(System.nanoTime() - startNanos, messages.size());
                }
                log.error("consume seckill order create buffer failed", e);
            }
        }
    }

}
