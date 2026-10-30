package com.example.priceparser.controller;

import java.util.Map;

import com.example.priceparser.model.ParseTask;
import com.example.priceparser.service.PriceRefreshScheduler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SchedulerController {

    private final PriceRefreshScheduler scheduler;

    public SchedulerController(PriceRefreshScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @GetMapping("/api/scheduler")
    public Map<String, Object> info() {
        return scheduler.getInfo();
    }

    /**
     * Запустить плановый обход сразу, не дожидаясь расписания.
     */
    @PostMapping("/api/scheduler/run")
    public ResponseEntity<ParseTask> runNow() {
        ParseTask task = scheduler.runNow();
        if (task == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(task);
    }
}
