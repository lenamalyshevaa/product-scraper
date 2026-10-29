package com.example.priceparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.example.priceparser.model.ParseTask;
import com.example.priceparser.model.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskServiceTest {

    private ThreadPoolExecutor executor;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        ParseService parseService = mock(ParseService.class);
        when(parseService.parse(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            Thread.sleep(100);
            if (url.contains("bad")) {
                throw new IllegalStateException("сайт не ответил");
            }
            Product product = new Product();
            product.setUrl(url);
            return product;
        });

        executor = new ThreadPoolExecutor(3, 3, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
        taskService = new TaskService(parseService, executor, mock(EventLog.class));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void taskIsProcessedInBackground() throws Exception {
        List<String> urls = List.of("https://a.ru/1", "https://a.ru/2", "https://a.ru/bad",
                "https://a.ru/3", "https://a.ru/4", "https://a.ru/5");

        ParseTask task = taskService.createTask(urls);

        // метод вернулся сразу, ссылки ещё обрабатываются
        assertEquals("IN_PROGRESS", task.getStatus());

        ParseTask finished = taskService.getTask(task.getId(), 5);
        assertEquals("DONE", finished.getStatus());
        assertEquals(6, finished.getProcessed());
        assertEquals(5, finished.getSuccess());
        assertEquals(1, finished.getFailed());
        assertNotNull(finished.getFinishedAt());
        assertTrue(executor.getCompletedTaskCount() >= 6);
    }

    @Test
    void unknownTaskReturnsNull() throws Exception {
        assertNull(taskService.getTask("no-such-id", 0));
    }
}
