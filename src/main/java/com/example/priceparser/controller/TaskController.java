package com.example.priceparser.controller;

import java.util.Map;

import com.example.priceparser.model.ParseTask;
import com.example.priceparser.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Создать фоновую задачу. Ответ приходит сразу, ссылки обрабатываются в пуле потоков.
     */
    @PostMapping("/api/tasks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ParseTask createTask(@Valid @RequestBody BatchRequest request) {
        return taskService.createTask(request.getUrls());
    }

    /**
     * Статус задачи. С параметром wait можно подождать окончания (в секундах, не больше 60).
     */
    @GetMapping("/api/tasks/{id}")
    public ParseTask getTask(@PathVariable String id, @RequestParam(defaultValue = "0") int wait)
            throws InterruptedException {
        ParseTask task = taskService.getTask(id, Math.min(wait, 60));
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Задача не найдена");
        }
        return task;
    }

    @GetMapping("/api/pool")
    public Map<String, Object> poolStats() {
        return taskService.getPoolStats();
    }
}
