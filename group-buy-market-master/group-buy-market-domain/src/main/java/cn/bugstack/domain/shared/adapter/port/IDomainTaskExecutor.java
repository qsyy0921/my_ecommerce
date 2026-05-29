package cn.bugstack.domain.shared.adapter.port;

/**
 * Domain port for asynchronous task execution.
 */
public interface IDomainTaskExecutor {

    void execute(Runnable task);

}
