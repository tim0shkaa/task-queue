# Task Queue System - Реактивные микросервисы

## Описание проекта

Система имитации очереди задач с реактивной архитектурой на Spring WebFlux. Проект состоит из двух микросервисов:
- **Service A (клиент)** - отправляет запросы через WebClient
- **Service B (сервер)** - обрабатывает запросы и возвращает данные в реактивном формате (Flux)

### Цель работы

Демонстрация работы с:
- WebClient для асинхронных HTTP-запросов
- Реактивными потоками данных (Mono/Flux)
- Фильтрами для логирования
- Обработкой ошибок и retry механизмами
- Неоптимальной бизнес-логикой (для последующего профилирования)

## Структура проекта

```
task-queue-system/
├── service-a/                  # Клиентский сервис
│   └── src/main/java/com/taskqueue/servicea/
│       ├── client/             # WebClient для запросов
│       ├── controller/         # REST endpoints
│       ├── filter/             # Логирование запросов
│       ├── config/             # Конфигурация WebClient
│       └── model/              # Модели данных
├── service-b/                  # Серверный сервис
│   └── src/main/java/com/taskqueue/serviceb/
│       ├── controller/         # REST API
│       ├── service/            # Неоптимальная бизнес-логика
│       ├── filter/             # Логирование запросов
│       └── model/              # Модели данных
├── build.gradle                # Общая конфигурация
└── settings.gradle             # Multi-project настройка
```

## Неоптимальная логика (Service B)

### Описание проблем производительности

Service B реализует **намеренно неэффективный** алгоритм обработки задач для демонстрации проблем производительности:

#### 1. Генерация 100,000 задач при каждом запросе
```java
private List<Task> generateTasks() {
    List<Task> tasks = new ArrayList<>(TASK_COUNT);  // TASK_COUNT = 100_000
    for (long i = 0; i < TASK_COUNT; i++) {
        // Создание объекта Task с Random данными
    }
}
```
**Проблема:** Огромная аллокация памяти и CPU время на каждый запрос

#### 2. Создание объектов-обёрток для каждой задачи
```java
private List<TaskWrapper> wrapTasks(List<Task> tasks) {
    List<TaskWrapper> wrapped = new ArrayList<>(tasks.size());
    for (Task task : tasks) {
        wrapped.add(new TaskWrapper(task));  // 100k новых объектов!
    }
}
```
**Проблема:** Дополнительные 100,000 объектов в памяти

#### 3. Тройная фильтрация с созданием промежуточных коллекций
```java
// Фильтр 1: по userId
List<TaskWrapper> filteredOnce = tasks.stream()
    .filter(wrapper -> wrapper.getTask().getUserId().equals(userId))
    .collect(Collectors.toList());

// Фильтр 2: по статусу
List<TaskWrapper> filteredTwice = filteredOnce.stream()
    .filter(wrapper -> wrapper.getTask().getStatus() != CANCELLED)
    .collect(Collectors.toList());

// Фильтр 3: по estimatedHours
List<TaskWrapper> filteredThrice = filteredTwice.stream()
    .filter(wrapper -> wrapper.getTask().getEstimatedHours() > 0)
    .collect(Collectors.toList());
```
**Проблема:** 
- 3 прохода по данным вместо одного
- Создание 3 промежуточных ArrayList
- Лишние итерации

#### 4. Двойная сортировка
```java
// Сортировка 1: по sortKey1
List<TaskWrapper> sortedOnce = new ArrayList<>(tasks);
sortedOnce.sort(new Comparator<TaskWrapper>() {
    @Override
    public int compare(TaskWrapper w1, TaskWrapper w2) {
        return Integer.compare(w1.getSortKey1(), w2.getSortKey1());
    }
});

// Сортировка 2: по priority и sortKey2
List<TaskWrapper> sortedTwice = new ArrayList<>(sortedOnce);
sortedTwice.sort(new Comparator<TaskWrapper>() {
    @Override
    public int compare(TaskWrapper w1, TaskWrapper w2) {
        // Сложная логика сравнения
    }
});
```
**Проблема:**
- 2 прохода сортировки (O(n log n) каждый)
- Создание анонимных классов Comparator при каждом вызове
- Копирование списков перед сортировкой

#### 5. Группировка с Collectors.groupingBy
```java
Map<String, List<TaskWrapper>> grouped = tasks.stream()
    .collect(Collectors.groupingBy(
        wrapper -> wrapper.getTask().getCategory(),
        Collectors.toList()
    ));
```
**Проблема:** Создание Map и List для каждой категории

#### 6. Разворачивание обёрток
```java
private List<Task> unwrapTasks(Map<String, List<TaskWrapper>> groupedTasks) {
    List<Task> result = new ArrayList<>();
    for (Map.Entry<String, List<TaskWrapper>> entry : groupedTasks.entrySet()) {
        for (TaskWrapper wrapper : entry.getValue()) {
            result.add(wrapper.getTask());  // Извлечение из обёртки
        }
    }
}
```
**Проблема:** Лишний проход по всем данным

### Итоговый pipeline неэффективности

```
Генерация 100k задач 
    ↓
Создание 100k обёрток (TaskWrapper)
    ↓
Фильтрация #1 (новый ArrayList)
    ↓
Фильтрация #2 (новый ArrayList)
    ↓
Фильтрация #3 (новый ArrayList)
    ↓
Сортировка #1 (копия + O(n log n))
    ↓
Сортировка #2 (копия + O(n log n))
    ↓
Группировка (Map + Lists)
    ↓
Разворачивание обёрток
    ↓
Возврат Flux<Task>
```

### Метрики производительности

**Ожидаемые проблемы:**
- Время обработки запроса: **5-15 секунд**
- Потребление памяти: **~500MB - 1GB** на запрос
- CPU usage: **высокий** во время обработки
- GC активность: **частые паузы**

### Возможности оптимизации (для ЛР №3)

1. **Кэширование** сгенерированных задач
2. **Объединение фильтров** в один проход
3. **Удаление обёрток** - работать напрямую с Task
4. **Одна сортировка** с комбинированным Comparator
5. **Stream API** вместо промежуточных коллекций
6. **Параллельные стримы** для обработки
7. **Lazy evaluation** с использованием Iterator/Stream
8. **Database** вместо in-memory генерации

## Реактивное программирование

### WebClient в Service A

#### Конфигурация с таймаутами
```java
HttpClient httpClient = HttpClient.create()
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
    .responseTimeout(Duration.ofSeconds(30))
    .doOnConnected(conn ->
        conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
            .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));
```

#### Retry с экспоненциальной задержкой
```java
.retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
    .maxBackoff(Duration.ofSeconds(10))
    .doBeforeRetry(retrySignal -> 
        logger.warn("Retrying request, attempt: {}", 
            retrySignal.totalRetries() + 1))
)
```

#### Обработка ошибок
```java
.onErrorResume(error -> {
    logger.error("Failed after retries: {}", error.getMessage());
    return Flux.empty();
})
```

### Операторы Reactor

**Используемые операторы:**
- `filter()` - фильтрация элементов потока
- `map()` - трансформация данных
- `collectList()` - сбор Flux в List
- `count()` - подсчет элементов
- `doOnNext()` - side-эффекты для каждого элемента
- `doOnComplete()` - действие при завершении
- `doOnError()` - обработка ошибок
- `timeout()` - таймаут операции

## Логирование

### WebFilter для входящих запросов

Оба сервиса используют `LoggingWebFilter`:

```java
@Component
public class LoggingWebFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String requestId = exchange.getRequest().getId();
        
        logger.info("[Service X] [{}] Incoming request: {} {}", 
            requestId, method, path);
        
        return chain.filter(exchange)
            .doOnSuccess(aVoid -> {
                long duration = System.currentTimeMillis() - startTime;
                logger.info("[Service X] [{}] Response: {} ms", requestId, duration);
            });
    }
}
```

### ExchangeFilterFunction для WebClient

Логирование исходящих запросов в Service A:

```java
private ExchangeFilterFunction logRequest() {
    return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
        logger.info("[Service A] Outgoing request: {} {}", 
            clientRequest.method(), clientRequest.url());
        return Mono.just(clientRequest);
    });
}

private ExchangeFilterFunction logResponse() {
    return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
        logger.info("[Service A] Response status: {}", 
            clientResponse.statusCode());
        return Mono.just(clientResponse);
    });
}
```

## Запуск проекта

### Требования

- Java 21
- Gradle 8.5+
- IntelliJ IDEA (рекомендуется)

### Способ 1: Через IntelliJ IDEA

1. Откройте проект в IntelliJ IDEA
2. Gradle автоматически импортирует зависимости

**Запуск Service B:**
- Найдите `ServiceBApplication.java` в `service-b`
- Run → Запустится на порту **8080**

**Запуск Service A:**
- Найдите `ServiceAApplication.java` в `service-a`
- Run → Запустится на порту **8081**

### Способ 2: Через командную строку

```bash
# Запуск Service B
./gradlew :service-b:bootRun

# В другом терминале запуск Service A
./gradlew :service-a:bootRun
```

### Способ 3: Сборка JAR

```bash
# Собрать оба сервиса
./gradlew build

# Запустить Service B
java -jar service-b/build/libs/service-b-1.0.0.jar

# Запустить Service A
java -jar service-a/build/libs/service-a-1.0.0.jar
```

## Тестирование API

### Проверка работоспособности

```bash
# Health check Service B
curl http://localhost:8080/api/tasks/health

# Health check Service A
curl http://localhost:8081/api/health
```

### Получение задач через Service A

**Базовый запрос (Flux - streaming):**
```bash
curl http://localhost:8081/api/user/user1/tasks
```

**Получить список (собрать в List):**
```bash
curl http://localhost:8081/api/user/user1/tasks/list
```

**Подсчет задач:**
```bash
curl http://localhost:8081/api/user/user1/tasks/count
```

**Фильтр по приоритету:**
```bash
curl http://localhost:8081/api/user/user1/tasks/filter
```

### Прямой запрос к Service B

```bash
curl http://localhost:8080/api/tasks/user1
```

## Примеры логов

### Service A (клиент)

```
2024-12-16 15:30:00 - [Service A] [abc123] Incoming request: GET /api/user/user1/tasks
2024-12-16 15:30:00 - [Service A Controller] Received request for user tasks: user1
2024-12-16 15:30:00 - [Service A] Outgoing request: GET http://localhost:8080/api/tasks/user1
2024-12-16 15:30:00 - [Service A] Request headers: [Accept:"application/json"]
2024-12-16 15:30:05 - [Service A] Response status: 200 OK
2024-12-16 15:30:05 - [Service A] Response headers: [Content-Type:"application/json"]
2024-12-16 15:30:05 - [Service A] Fetching tasks for user: user1
2024-12-16 15:30:05 - [Service A Controller] Completed streaming tasks for user: user1
2024-12-16 15:30:05 - [Service A] [abc123] Response: /api/user/user1/tasks - 5234 ms - Status: 200
```

### Service B (сервер)

```
2024-12-16 15:30:00 - [Service B] [xyz789] Incoming request: GET /api/tasks/user1
2024-12-16 15:30:05 - [Service B] [xyz789] Response: /api/tasks/user1 - 5180 ms - Status: 200
```

## Анализ неоптимального кода

### Как измерить производительность

1. **Время выполнения:**
```bash
time curl http://localhost:8081/api/user/user1/tasks/count
```

2. **Memory usage:**
```bash
# В процессе работы Service B
jcmd <pid> VM.native_memory summary
```

3. **Профилирование (ЛР №3):**
- JProfiler
- VisualVM
- Async Profiler
- Flight Recorder

### Точки для профилирования

Наиболее проблемные методы в `TaskService`:

1. `generateTasks()` - генерация 100k объектов
2. `wrapTasks()` - создание обёрток
3. `firstFilter()`, `secondFilter()`, `thirdFilter()` - множественные фильтрации
4. `firstSort()`, `secondSort()` - двойная сортировка
5. `groupTasks()` - группировка с Collectors

## Тестирование

### Запуск тестов

```bash
# Все тесты
./gradlew test

# Только Service A
./gradlew :service-a:test

# Только Service B
./gradlew :service-b:test
```

### Покрытие тестами

**Service B:**
- `TaskServiceTest` - тестирование генерации и фильтрации задач

**Service A:**
- `TaskClientTest` - тестирование WebClient и retry логики

