package com.example.priceparser.service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Журнал событий парсера в файле logs/parser-events.log.
 *
 * Потоки, которые парсят страницы, не пишут в файл сами: они кладут строку в очередь
 * и сразу идут дальше. Из очереди строки забирает отдельный поток-демон и пишет их в файл.
 * Демон не мешает приложению завершиться.
 */
@Component
public class EventLog {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    // специальная строка, по которой поток-писатель понимает, что пора остановиться
    private static final String STOP = "__STOP__";

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    @Value("${parser.event-log-file}")
    private String logFile;

    private Thread writerThread;

    @PostConstruct
    public void start() {
        File file = new File(logFile);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        writerThread = new Thread(this::writeLoop, "event-log-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    public void log(String message) {
        String line = LocalDateTime.now().format(TIME_FORMAT)
                + " [" + Thread.currentThread().getName() + "] " + message;
        queue.offer(line);
    }

    private void writeLoop() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            while (true) {
                // take() ждёт, пока в очереди что-то появится
                String line = queue.take();
                if (STOP.equals(line)) {
                    break;
                }
                writer.write(line);
                writer.newLine();
                // сбрасываем на диск, когда очередь разобрана
                if (queue.isEmpty()) {
                    writer.flush();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("Не удалось записать журнал событий: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        // дописываем всё, что осталось в очереди, и останавливаем поток
        queue.offer(STOP);
        writerThread.join(2000);
    }
}
