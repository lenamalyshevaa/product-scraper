### Product Scraper

Сервис на Spring Boot, который собирает данные о товарах по ссылкам: название, краткое описание, цену, валюту, наличие и дату публикации. Может использоваться как агрегатор цен.

Автор: Малышева Елена Алексеевна

Основной источник - книжный магазин [Лабиринт](https://www.labirint.ru). Он отдаёт страницы без капчи, а цена и наличие размечены по стандарту schema.org (`itemprop="price"`). По той же разметке работают и ссылки на [book24](https://book24.ru).

### Запуск

Нужна JDK 17 или новее. Maven ставить не нужно, в проекте есть Maven Wrapper.

IntelliJ IDEA: открыть папку проекта, открыть класс `PriceParserApplication` и нажать зелёный треугольник рядом с ним.

Из терминала:

```bash
./mvnw spring-boot:run        # macOS / Linux
mvnw.cmd spring-boot:run      # Windows
```

Сервис запускается на `http://localhost:8080`.

### Проверка

Запросы можно отправить через curl (терминал в IDEA) или Postman. Для Postman есть готовая коллекция: `postman/product-scraper.postman_collection.json` (File - Import).

1. Отправить ссылку на книгу:

```bash
curl -X POST http://localhost:8080/api/parse \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.labirint.ru/books/982366/"}'
```

Тело запроса:

```json
{
  "url": "https://www.labirint.ru/books/982366/"
}
```

Ответ `201 Created`:

```json
{
  "id": 1,
  "url": "https://www.labirint.ru/books/982366/",
  "title": "Волосы Вероники: Фрэнсис Фицджеральд",
  "description": "Один из крупнейших американских прозаиков прошлого века Фрэнсис Скотт Фицджеральд (1896-1940) ...",
  "price": 618,
  "currency": "RUB",
  "availability": "InStock",
  "publishedAt": "2023-01-01T00:00:00Z",
  "parsedAt": "2026-10-27T16:05:41.120Z"
}
```

2. Получить все собранные товары (новые сверху), можно прямо в браузере:

```
GET http://localhost:8080/answer
```

3. Отправить сразу несколько ссылок, в том числе страницу каталога:

```bash
curl -X POST http://localhost:8080/api/parse/batch \
  -H "Content-Type: application/json" \
  -d '{"urls": ["https://www.labirint.ru/genres/2529/", "https://www.labirint.ru/books/982366/", "https://book24.ru/product/novyj-god-na-zelenoj-25-8844240/"]}'
```

Ссылки обрабатываются параллельно в 4 потоках. Страница каталога раскрывается в ссылки на книги (не больше 20), и их тоже разбирают все потоки. Ответ:

```json
{
  "total": 22,
  "success": 22,
  "failed": 0,
  "timeMs": 7169,
  "products": [
    { "title": "Волосы Вероники: Фрэнсис Фицджеральд", "price": 618, "threadName": "batch-worker-4", "...": "..." }
  ],
  "errors": []
}
```

В поле `threadName` видно, какой поток обработал ссылку. Если одна ссылка не открылась, она попадает в `errors`, а остальные обрабатываются дальше.

4. Фоновая задача: ответ приходит сразу, ссылки обрабатываются в пуле потоков:

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"urls": ["https://www.labirint.ru/books/982366/", "https://www.labirint.ru/books/1032572/", "https://book24.ru/product/novyj-god-na-zelenoj-25-8844240/"]}'
```

Ответ `202 Accepted` с `id` и `"status": "IN_PROGRESS"`. Статус задачи (с `wait=30` запрос подождёт окончания до 30 секунд):

```
GET http://localhost:8080/api/tasks/{id}?wait=30
```

Когда всё готово, `status` становится `DONE`, в `products` лежат товары, в `errors` - ссылки, которые не удалось разобрать.

5. Состояние пула потоков:

```
GET http://localhost:8080/api/pool
```

```json
{ "poolSize": 6, "activeThreads": 6, "queueSize": 3, "completedTasks": 0, "tasksCreated": 1 }
```

6. Результаты из файла `data/results.jsonl` (сохраняются и после перезапуска):

```
GET http://localhost:8080/api/file-results?limit=20
```

Журнал событий пишется в `logs/parser-events.log`:

```
2026-10-29 18:40:00.755 [http-nio-8080-exec-2] Задача c232f222-...: 9 ссылок
2026-10-29 18:40:00.755 [pool-worker-1] Загрузка https://www.labirint.ru/books/1011055/
2026-10-29 18:40:01.902 [pool-worker-1] Готово https://www.labirint.ru/books/1011055/: ..., 899 RUB
```

Другие ссылки для проверки:

- `https://www.labirint.ru/books/1032572/` - ещё одна книга Лабиринта
- `https://book24.ru/product/novyj-god-na-zelenoj-25-8844240/` - книга из book24
- `https://www.labirint.ru/genres/2529/` - страница каталога, вернёт ошибку 422

Цены в магазине меняются, поэтому сумма может отличаться от примера. Если книга пропала из продажи, подойдёт любая ссылка вида `https://www.labirint.ru/books/<номер>/`.

### Ошибки

| Код | Когда |
|-----|-------|
| 400 | в `url` не ссылка или в ней есть пробелы, пустой список `urls` или больше 50 ссылок |
| 422 | на странице нет цены или это страница каталога |
| 404 | задача с таким `id` не найдена |
| 502 | страницу не удалось загрузить |

### Откуда берутся данные

- цена и валюта - `itemprop="price"` и `itemprop="priceCurrency"`, если их нет - мета-теги или элемент с классом `price`;
- название - заголовок `h1`;
- описание - блок "Аннотация" (Лабиринт) или `itemprop="description"` (book24), обрезается до 500 символов;
- дата публикации - `itemprop="datePublished"`; у Лабиринта точной даты нет, поэтому берётся год издания из характеристик; если нет и его - заголовок ответа `Last-Modified`.

### Как устроена многопоточность

`BatchParseService` создаёт 4 потока `Thread`, каждому даёт задачу `ParseWorker` (`Runnable`) и ждёт их через `join()`.

- ссылки лежат в общей очереди `ConcurrentLinkedQueue`, каждый поток забирает следующую через `poll()`;
- результаты и ошибки собираются в `Collections.synchronizedList`;
- уже встреченные ссылки хранятся в `ConcurrentHashMap.newKeySet()`, чтобы одна книга не обрабатывалась дважды;
- счётчик `AtomicInteger` считает необработанные ссылки: поток не завершается, пока другой поток может добавить в очередь ссылки из каталога.

Число потоков и лимит книг с каталога задаются в `application.yml` (`parser.batch-threads`, `parser.catalog-max-products`).

Фоновые задачи `/api/tasks`:

- `ExecutorConfig` создаёт свой `ThreadPoolExecutor` на 6 потоков `pool-worker-N` с ограниченной очередью `ArrayBlockingQueue` на 100 задач; если очередь заполнена, задачу выполняет поток, который её отправил (`CallerRunsPolicy`);
- каждая ссылка - отдельная задача в пуле; задачи хранятся в `ConcurrentHashMap`;
- у задачи есть `CountDownLatch` на число ссылок: каждая обработанная ссылка делает `countDown()`, а запрос с `wait` ждёт через `await()`; счётчики успехов и ошибок - `AtomicInteger`.

Ограничение запросов к сайту: `HostLimiter` держит свой `Semaphore` для каждого сайта. К одному сайту одновременно уходит не больше 3 запросов, остальные потоки ждут. Разные сайты друг другу не мешают.

Журнал событий: `EventLog` - потоки кладут строки в `LinkedBlockingQueue`, а отдельный поток-демон `event-log-writer` забирает их через `take()` и пишет в файл. Парсинг не ждёт записи на диск.

Хранение в файле: `ResultFileStorage` дописывает каждый товар строкой JSON в `data/results.jsonl`. Доступ к файлу защищён `ReentrantReadWriteLock`: читать можно одновременно, писать - по одному.

Настройки в `application.yml`: `parser.pool-threads`, `parser.pool-queue-size`, `parser.max-requests-per-site`, `parser.event-log-file`, `parser.results-file`.

### Тесты

```bash
./mvnw test
```

Тесты не ходят в интернет: парсер проверяется на кусках разметки Лабиринта и book24, а в тестах контроллера и параллельной обработки загрузка страниц заменена заглушкой. Тест `BatchParseServiceTest` проверяет, что ссылки обработали разные потоки и что это быстрее, чем по очереди. `TaskServiceTest` проверяет фоновую задачу в пуле, `HostLimiterTest` - что к одному сайту не уходит больше двух запросов сразу, `ResultFileStorageTest` - что 8 потоков пишут в файл одновременно и ни одна строка не теряется.

### Структура

```
src/main/java/com/example/priceparser
├── config       настройка WebClient и пула потоков
├── controller   REST-контроллер и обработка ошибок
├── model        товар, результат пакета, фоновая задача
├── parser       разбор HTML и извлечение цены
└── service      загрузка страниц, обработка ссылок, потоки, хранение, журнал
```
