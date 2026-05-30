package cn.bugstack.infrastructure.event;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
public class SeckillLocalOrderCreateBuffer {

    private BlockingQueue<String> localQueue = new ArrayBlockingQueue<>(1);

    public void init(Integer localCapacity) {
        localQueue = new ArrayBlockingQueue<>(Math.max(1, null == localCapacity ? 1 : localCapacity));
    }

    public boolean offer(String message) {
        return localQueue.offer(message);
    }

    public List<SeckillOrderBufferMessage> pollBatch(int batchSize, long timeout, TimeUnit timeUnit) throws InterruptedException {
        int size = Math.max(1, batchSize);
        String message = localQueue.poll(timeout, timeUnit);
        if (null == message) {
            return Collections.emptyList();
        }
        List<SeckillOrderBufferMessage> result = new ArrayList<>(size);
        result.add(new SeckillOrderBufferMessage(null, message, -1, null));
        List<String> drained = new ArrayList<>(size - 1);
        localQueue.drainTo(drained, size - 1);
        for (String item : drained) {
            result.add(new SeckillOrderBufferMessage(null, item, -1, null));
        }
        return result;
    }

    public int size() {
        return null == localQueue ? 0 : localQueue.size();
    }

}
