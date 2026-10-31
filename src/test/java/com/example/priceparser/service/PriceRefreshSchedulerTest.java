package com.example.priceparser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.example.priceparser.model.ParseTask;
import com.example.priceparser.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PriceRefreshSchedulerTest {

    private TaskService taskService;
    private ProductRepository repository;
    private PriceRefreshScheduler scheduler;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        repository = mock(ProductRepository.class);
        scheduler = new PriceRefreshScheduler(taskService, repository, mock(EventLog.class));
        ReflectionTestUtils.setField(scheduler, "seedUrls", new String[] {"https://www.labirint.ru/books/1/"});
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
    }

    @Test
    void runTakesSeedUrlsAndKnownProducts() {
        // книга 1 есть и в стартовых ссылках, и в базе - не должна задвоиться
        when(repository.findAllUrls()).thenReturn(List.of(
                "https://www.labirint.ru/books/2/", "https://www.labirint.ru/books/1/"));
        when(taskService.createTask(anyList())).thenReturn(new ParseTask("t1", 2));

        scheduler.runNow();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskService).createTask(captor.capture());
        assertEquals(List.of("https://www.labirint.ru/books/1/", "https://www.labirint.ru/books/2/"), captor.getValue());
    }

    @Test
    void secondRunIsSkippedWhileFirstIsInProgress() {
        // задача на 1 ссылку, которая ещё не обработана
        ParseTask running = new ParseTask("t1", 1);
        when(taskService.createTask(anyList())).thenReturn(running);

        ParseTask first = scheduler.runNow();
        ParseTask second = scheduler.runNow();

        assertSame(first, second);
        verify(taskService, times(1)).createTask(anyList());
    }

    @Test
    void scheduleStartsFirstRunAfterDelay() throws Exception {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "initialDelaySeconds", 0);
        ReflectionTestUtils.setField(scheduler, "intervalMinutes", 10);
        when(taskService.createTask(anyList())).thenReturn(new ParseTask("t1", 0));

        scheduler.start();
        Thread.sleep(300);

        verify(taskService, times(1)).createTask(anyList());
        assertEquals(1, scheduler.getInfo().get("runs"));
    }

    @Test
    void nothingToRefresh() {
        ReflectionTestUtils.setField(scheduler, "seedUrls", new String[0]);

        assertNull(scheduler.runNow());
    }
}
