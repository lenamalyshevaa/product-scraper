package com.example.priceparser.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

    @Value("${parser.pool-threads}")
    private int poolThreads;

    @Value("${parser.pool-queue-size}")
    private int poolQueueSize;

    /**
     * Свой пул потоков для фоновых задач.
     * Задачи ждут в ограниченной очереди ArrayBlockingQueue. Если она переполнится,
     * задачу выполнит сам поток, который её отправил (CallerRunsPolicy), - так пул не теряет задачи.
     * При остановке приложения Spring сам вызовет shutdown().
     */
    @Bean
    public ThreadPoolExecutor parserExecutor() {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> new Thread(runnable, "pool-worker-" + counter.getAndIncrement());

        return new ThreadPoolExecutor(
                poolThreads,
                poolThreads,
                60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(poolQueueSize),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
