### Product Scraper

Многопоточный сервис на Spring Boot, который собирает данные о товарах по ссылкам: название, краткое описание, цену, валюту, наличие и дату публикации. Повторно обходит ссылки по расписанию и хранит историю цен, поэтому может использоваться как агрегатор цен.

Автор: Малышева Елена Алексеевна

Основной источник - книжный магазин [Лабиринт](https://www.labirint.ru). Он отдаёт страницы без капчи, а цена и наличие размечены по стандарту schema.org (`itemprop="price"`). По той же разметке работают и ссылки на [book24](https://book24.ru).

### Запуск

Нужна JDK 17 или новее. Maven, база данных и другие программы не нужны: Maven Wrapper лежит в проекте, база H2 встроена в приложение.

IntelliJ IDEA: открыть папку проекта, открыть класс `PriceParserApplication` и нажать зелёный треугольник рядом с ним.

Из терминала:

```bash
./mvnw spring-boot:run        # macOS / Linux
mvnw.cmd spring-boot:run      # Windows
```

Сервис запускается на `http://localhost:8080`. Через 5 секунд после запуска планировщик сам обходит 8 стартовых ссылок (книги Лабиринта и book24) и дальше повторяет обход каждые 10 минут.

### Быстрая проверка

Запросы можно отправлять через curl (терминал в IDEA), Postman или браузер (для GET). Для Postman есть готовая коллекция `postman/product-scraper.postman_collection.json` (File - Import).

1. Запустить приложение и подождать 10-15 секунд.

2. Открыть в браузере результаты, которые собрал планировщик:

```
GET http://localhost:8080/answer
```

```json
{
  "content": [
    {
      "id": 8,
      "url": "https://www.labirint.ru/books/982366/",
      "title": "Волосы Вероники: Фрэнсис Фицджеральд",
      "description": "Один из крупнейших американских прозаиков прошлого века Фрэнсис Скотт Фицджеральд (1896-1940) ...",
      "price": 529.00,
      "currency": "RUB",
      "availability": "InStock",
      "publishedAt": "2023-01-01T00:00:00Z",
      "parsedAt": "2026-11-02T09:01:26.702Z",
      "threadName": "pool-worker-1"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 8,
  "totalPages": 1,
  "sort": "parsedAt",
  "order": "desc"
}
```

3. Фильтры и сортировка (выполняются через `parallelStream`):

```
GET http://localhost:8080/answer?sort=price&order=asc
GET http://localhost:8080/answer?maxPrice=1000&site=labirint
GET http://localhost:8080/answer?q=дракула
GET http://localhost:8080/answer?sort=title&order=asc&page=1&size=3
```

4. Отправить свою ссылку на книгу:

```bash
curl -X POST http://localhost:8080/api/parse \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.labirint.ru/books/1032572/"}'
```

Тело запроса:

```json
{
  "url": "https://www.labirint.ru/books/1032572/"
}
```

Ответ `201 Created` - товар в том же формате, что и в `/answer`.

5. Несколько ссылок и страница каталога, обработка в 4 потоках `Thread`:

```bash
curl -X POST http://localhost:8080/api/parse/batch \
  -H "Content-Type: application/json" \
  -d '{"urls": ["https://www.labirint.ru/genres/2529/", "https://www.labirint.ru/books/982366/"]}'
```

Страница каталога раскрывается в ссылки на книги (не больше 20). В ответе `total`, `success`, `failed`, `timeMs`, список `products` и `errors`; в поле `threadName` у каждого товара видно, какой поток его обработал.

6. Фоновая задача в пуле потоков: ответ `202 Accepted` приходит сразу, с `id` задачи:

```bash
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"urls": ["https://www.labirint.ru/books/1024699/", "https://book24.ru/product/novyj-god-na-zelenoj-25-8844240/"]}'
```

```
GET http://localhost:8080/api/tasks/{id}?wait=30
```

С `wait=30` запрос ждёт окончания задачи до 30 секунд. Когда всё готово, `status` становится `DONE`.

7. История цены по одной ссылке (каждый обход планировщика добавляет запись):

```
GET http://localhost:8080/answer/history?url=https://www.labirint.ru/books/982366/
```

8. Планировщик и пул потоков:

```
GET  http://localhost:8080/api/scheduler
POST http://localhost:8080/api/scheduler/run
GET  http://localhost:8080/api/pool
```

9. Результаты из файла, журнал событий и база:

- `GET http://localhost:8080/api/file-results?limit=20` - последние записи из `data/results.jsonl`;
- `logs/parser-events.log` - журнал событий, видно, какой поток что делал;
- `http://localhost:8080/h2-console` - консоль базы: JDBC URL `jdbc:h2:file:./data/productdb;AUTO_SERVER=TRUE`, пользователь `sa`, пароль пустой, таблица `PRODUCTS`.

Цены в магазине меняются, поэтому суммы могут отличаться от примеров. Если книга пропала из продажи, подойдёт любая ссылка вида `https://www.labirint.ru/books/<номер>/`.

### Эндпоинты

| Метод | Адрес | Что делает |
|-------|-------|------------|
| GET | `/answer` | результаты из H2 с фильтрами, сортировкой и страницами |
| GET | `/answer/history?url=` | история цены по ссылке |
| POST | `/api/parse` | обработать одну ссылку |
| POST | `/api/parse/batch` | обработать список ссылок в нескольких потоках |
| POST | `/api/tasks` | создать фоновую задачу |
| GET | `/api/tasks/{id}?wait=` | статус фоновой задачи |
| GET | `/api/pool` | состояние пула потоков |
| GET | `/api/scheduler` | состояние планировщика |
| POST | `/api/scheduler/run` | запустить обход вне расписания |
| GET | `/api/file-results?limit=` | результаты из файла |

### Параметры /answer

| Параметр | По умолчанию | Описание |
|----------|--------------|----------|
| `q` | | поиск по названию и описанию без учёта регистра |
| `minPrice`, `maxPrice` | | диапазон цены |
| `currency` | | валюта, например `RUB` |
| `site` | | часть адреса сайта: `labirint`, `book24` |
| `latestOnly` | `true` | `true` - последний обход каждой ссылки, `false` - вся история |
| `sort` | `parsedAt` | `parsedAt`, `price`, `title`, `publishedAt` |
| `order` | `desc` | `asc` или `desc` |
| `page` | `0` | номер страницы с нуля |
| `size` | `20` | размер страницы, от 1 до 100 |

### Ошибки

Все ошибки приходят в одном формате: `{"timestamp", "status", "error", "message"}`.

| Код | Когда |
|-----|-------|
| 400 | неверная ссылка, пустой список `urls` или больше 50 ссылок, неверный параметр `/answer` |
| 404 | задача не найдена или по ссылке нет истории |
| 422 | на странице нет цены или это страница каталога |
| 502 | страницу не удалось загрузить |

### Что где реализовано

| Что | Где |
|-----|-----|
| Spring Boot, MVC, unit-тесты | `controller`, `service`, `src/test` |
| Простые потоки `Thread` и `Runnable` | `BatchParseService`, `ParseWorker` |
| Собственный `ExecutorService` | `ExecutorConfig` - `ThreadPoolExecutor` на 6 потоков с очередью `ArrayBlockingQueue` |
| Запуск по расписанию | `PriceRefreshScheduler` - `ScheduledExecutorService`, `scheduleWithFixedDelay` |
| Блокирующие структуры | `LinkedBlockingQueue` в журнале событий, `ArrayBlockingQueue` в пуле |
| Примитивы синхронизации | `Semaphore` (`HostLimiter`), `CountDownLatch` (`ParseTask`), `ReentrantReadWriteLock` (`ResultFileStorage`), `synchronized` |
| Хранение в своём файле | `ResultFileStorage` - `data/results.jsonl` |
| Поток-демон для логирования | `EventLog` - поток `event-log-writer` пишет `logs/parser-events.log` |
| Встроенная база H2 | `Product` (`@Entity`), `ProductRepository`, файл `data/productdb.mv.db` |
| Фильтрация и сортировка через parallel stream | `AnswerService` |
| Межсервисное взаимодействие | `PageLoader` - `WebClient` из WebFlux |
| Потокобезопасные структуры | `ConcurrentHashMap`, `ConcurrentLinkedQueue`, `Collections.synchronizedList`, `AtomicInteger` |

### Как устроена многопоточность

Пакетная обработка `/api/parse/batch`: `BatchParseService` создаёт 4 потока `Thread`, каждому даёт задачу `ParseWorker` (`Runnable`) и ждёт их через `join()`.

- ссылки лежат в общей очереди `ConcurrentLinkedQueue`, каждый поток забирает следующую через `poll()`;
- результаты и ошибки собираются в `Collections.synchronizedList`;
- уже встреченные ссылки хранятся в `ConcurrentHashMap.newKeySet()`, чтобы одна книга не обрабатывалась дважды;
- счётчик `AtomicInteger` считает необработанные ссылки: поток не завершается, пока другой поток может добавить в очередь ссылки из каталога.

Фоновые задачи `/api/tasks`:

- `ExecutorConfig` создаёт свой `ThreadPoolExecutor` на 6 потоков `pool-worker-N` с ограниченной очередью `ArrayBlockingQueue` на 100 задач; если очередь заполнена, задачу выполняет поток, который её отправил (`CallerRunsPolicy`);
- каждая ссылка - отдельная задача в пуле; задачи хранятся в `ConcurrentHashMap`;
- у задачи есть `CountDownLatch` на число ссылок: каждая обработанная ссылка делает `countDown()`, а запрос с `wait` ждёт через `await()`; счётчики успехов и ошибок - `AtomicInteger`.

Планировщик: `PriceRefreshScheduler` работает на `ScheduledExecutorService` с одним потоком-демоном `price-scheduler`. Через `scheduleWithFixedDelay` он раз в 10 минут запускает обход стартовых ссылок и всех ссылок из базы фоновой задачей в общем пуле. Если прошлый обход ещё не закончился, новый не запускается. Исключения внутри перехватываются: иначе `ScheduledExecutorService` молча отменил бы все следующие запуски.

Ограничение запросов к сайту: `HostLimiter` держит свой `Semaphore` для каждого сайта. К одному сайту одновременно уходит не больше 3 запросов, остальные потоки ждут. Разные сайты друг другу не мешают.

Журнал событий: `EventLog` - потоки кладут строки в `LinkedBlockingQueue`, а отдельный поток-демон `event-log-writer` забирает их через `take()` и пишет в файл. Парсинг не ждёт записи на диск.

Хранение в файле: `ResultFileStorage` дописывает каждый товар строкой JSON в `data/results.jsonl`. Доступ к файлу защищён `ReentrantReadWriteLock`: читать можно одновременно, писать - по одному.

Выборка `/answer`: `AnswerService` берёт все записи из H2 и обрабатывает их через `parallelStream()`. Сначала `Collectors.toConcurrentMap` оставляет для каждой ссылки последний обход, потом `filter()` применяет фильтры, `sorted()` сортирует, и из результата вырезается нужная страница.

### Откуда берутся данные

- цена и валюта - `itemprop="price"` и `itemprop="priceCurrency"`, если их нет - мета-теги или элемент с классом `price`;
- название - заголовок `h1`;
- описание - блок "Аннотация" (Лабиринт) или `itemprop="description"` (book24), обрезается до 500 символов;
- дата публикации - `itemprop="datePublished"`; у Лабиринта точной даты нет, поэтому берётся год издания из характеристик; если нет и его - заголовок ответа `Last-Modified`.

### Настройки

Все настройки в `src/main/resources/application.yml`, блок `parser`:

| Параметр | Значение | Что задаёт |
|----------|----------|------------|
| `batch-threads` | 4 | потоки для `/api/parse/batch` |
| `pool-threads`, `pool-queue-size` | 6, 100 | пул для фоновых задач |
| `max-requests-per-site` | 3 | одновременные запросы к одному сайту |
| `retry-attempts`, `retry-delay-ms` | 3, 1000 | повторы загрузки, если сайт не ответил или вернул 5xx |
| `catalog-max-products` | 20 | сколько книг брать с одной страницы каталога |
| `scheduler.interval-minutes` | 10 | период повторного обхода |
| `scheduler.seed-urls` | 8 ссылок | стартовые ссылки |
| `results-file`, `event-log-file` | | файлы результатов и журнала |

### Тесты

```bash
./mvnw test
```

Тесты не ходят в интернет: парсер проверяется на кусках разметки Лабиринта и book24, а в остальных тестах загрузка страниц заменена заглушкой.

- `PriceExtractorTest`, `ProductPageParserTest` - извлечение цены и разбор страниц;
- `BatchParseServiceTest` - ссылки обрабатывают разные потоки, и это быстрее, чем по очереди;
- `TaskServiceTest` - фоновая задача в пуле;
- `HostLimiterTest` - к одному сайту не уходит больше двух запросов сразу;
- `PageLoaderTest` - повтор загрузки после ошибки 5xx и без повтора для 404 (на тестовом HTTP-сервере из JDK);
- `ResultFileStorageTest` - 8 потоков пишут в файл одновременно, ни одна строка не теряется;
- `PriceRefreshSchedulerTest` - запуск по расписанию и пропуск обхода, пока идёт прошлый;
- `ProductRepositoryTest` - запросы к H2;
- `AnswerServiceTest` - фильтры, сортировка и страницы;
- `ProductControllerTest` - эндпоинты и формат ошибок.

### Структура

```
src/main/java/com/example/priceparser
├── config       WebClient и пул потоков
├── controller   REST-контроллеры и обработка ошибок
├── model        товар, фильтр, страница, фоновая задача
├── parser       разбор HTML и извлечение цены
├── repository   доступ к H2
└── service      загрузка страниц, потоки, планировщик, выборка, файл, журнал
```
