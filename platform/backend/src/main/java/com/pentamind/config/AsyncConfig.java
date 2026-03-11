package com.pentamind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * 用于执行诸如 Nmap、Subfinder 等长耗时的安全扫描外部进程。
 */
@Configuration
public class AsyncConfig {

    public static final String SCAN_TASK_EXECUTOR = "scanTaskExecutor";

    @Bean(name = SCAN_TASK_EXECUTOR)
    public Executor scanTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：根据目标并发扫描数量决定
        executor.setCorePoolSize(10);
        // 最大线程数：支持的最大突发扫描进程数
        executor.setMaxPoolSize(50);
        // 队列容量：缓冲的任务
        executor.setQueueCapacity(200);
        // 线程活跃时间（秒）
        executor.setKeepAliveSeconds(60);
        // 线程名前缀
        executor.setThreadNamePrefix("Scanner-Worker-");
        // 拒绝策略：由调用线程处理（该策略会直接在 execute 方法的调用线程中运行被拒绝的任务；如果执行程序已关闭，则会丢弃该任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 执行初始化
        executor.initialize();
        return executor;
    }
}
