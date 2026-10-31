package com.example.priceparser.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.priceparser.model.ParseTask;
import com.example.priceparser.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Планировщик: по расписанию заново обходит все известные ссылки и обновляет цены.
 * Работает на ScheduledExecutorService с одним потоком "price-scheduler".
 * Сам обход идёт фоновой задачей в общем пуле, планировщик только запускает её.
 */
@Component
public class PriceRefreshScheduler {

    private final TaskService taskService;
    private final ProductRepository repository;
    private final EventLog eventLog;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "price-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicInteger runs = new AtomicInteger();
    private volatile ParseTask lastTask;
    private volatile Instant lastRunAt;

    @Value("${parser.scheduler.enabled}")
    private boolean enabled;

    @Value("${parser.scheduler.initial-delay-seconds}")
    private int initialDelaySeconds;

    @Value("${parser.scheduler.interval-minutes}")
    private int intervalMinutes;

    // ссылки для первого обхода, чтобы сразу после запуска были данные
    @Value("${parser.scheduler.seed-urls}")
    private String[] seedUrls;

    public PriceRefreshScheduler(TaskService taskService, ProductRepository repository, EventLog eventLog) {
        this.taskService = taskService;
        this.repository = repository;
        this.eventLog = eventLog;
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            return;
        }
        // fixed delay: следующий запуск через interval после окончания предыдущего, запуски не наслаиваются
        scheduler.scheduleWithFixedDelay(this::scheduledRun, initialDelaySeconds,
                intervalMinutes * 60L, TimeUnit.SECONDS);
        eventLog.log("Планировщик запущен: каждые " + intervalMinutes + " мин");
    }

    private void scheduledRun() {
        // если исключение вылетит из задачи, ScheduledExecutorService молча отменит все следующие запуски
        try {
            runNow();
        } catch (Exception e) {
            eventLog.log("Ошибка планировщика: " + e.getMessage());
        }
    }

    /**
     * Запускает обход прямо сейчас. Если прошлый обход ещё идёт, новый не запускается.
     */
    public synchronized ParseTask runNow() {
        if (lastTask != null && !"DONE".equals(lastTask.getStatus())) {
            eventLog.log("Прошлый обход ещё идёт, пропускаем");
            return lastTask;
        }

        Set<String> urls = new LinkedHashSet<>();
        for (String url : seedUrls) {
            if (!url.isBlank()) {
                urls.add(url.trim());
            }
        }
        urls.addAll(repository.findAllUrls());
        if (urls.isEmpty()) {
            eventLog.log("Обновлять нечего: ещё нет ни одного товара");
            return null;
        }

        lastTask = taskService.createTask(new ArrayList<>(urls));
        lastRunAt = Instant.now();
        runs.incrementAndGet();
        eventLog.log("Плановый обход: " + urls.size() + " ссылок, задача " + lastTask.getId());
        return lastTask;
    }

    public Map<String, Object> getInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("enabled", enabled);
        info.put("intervalMinutes", intervalMinutes);
        info.put("runs", runs.get());
        info.put("lastRunAt", lastRunAt);
        info.put("lastTaskId", lastTask == null ? null : lastTask.getId());
        info.put("lastTaskStatus", lastTask == null ? null : lastTask.getStatus());
        return info;
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }
}
