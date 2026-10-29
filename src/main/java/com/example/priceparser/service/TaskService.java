package com.example.priceparser.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

import com.example.priceparser.model.ParseTask;
import com.example.priceparser.model.Product;
import org.springframework.stereotype.Service;

/**
 * Фоновые задачи: запрос сразу получает id задачи, а ссылки обрабатываются в пуле потоков.
 */
@Service
public class TaskService {

    private final ParseService parseService;
    private final ThreadPoolExecutor executor;
    private final EventLog eventLog;

    // задачи читают и создают разные потоки Tomcat, поэтому ConcurrentHashMap
    private final Map<String, ParseTask> tasks = new ConcurrentHashMap<>();

    public TaskService(ParseService parseService, ThreadPoolExecutor parserExecutor, EventLog eventLog) {
        this.parseService = parseService;
        this.executor = parserExecutor;
        this.eventLog = eventLog;
    }

    public ParseTask createTask(List<String> urls) {
        ParseTask task = new ParseTask(UUID.randomUUID().toString(), urls.size());
        tasks.put(task.getId(), task);
        eventLog.log("Задача " + task.getId() + ": " + urls.size() + " ссылок");

        for (String url : urls) {
            executor.execute(() -> {
                try {
                    Product product = parseService.parse(url);
                    task.addProduct(product);
                } catch (Exception e) {
                    task.addError(url, e.getMessage());
                } finally {
                    task.linkDone();
                }
            });
        }
        return task;
    }

    /**
     * Возвращает задачу. Если waitSeconds больше нуля, ждёт её окончания столько секунд.
     */
    public ParseTask getTask(String id, int waitSeconds) throws InterruptedException {
        ParseTask task = tasks.get(id);
        if (task != null && waitSeconds > 0) {
            task.await(waitSeconds);
        }
        return task;
    }

    public Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("poolSize", executor.getPoolSize());
        stats.put("activeThreads", executor.getActiveCount());
        stats.put("queueSize", executor.getQueue().size());
        stats.put("completedTasks", executor.getCompletedTaskCount());
        stats.put("tasksCreated", tasks.size());
        return stats;
    }
}
