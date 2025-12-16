package com.taskqueue.servicea.controller;

import com.taskqueue.servicea.client.TaskClient;
import com.taskqueue.servicea.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TaskProxyController {

    private static final Logger logger = LoggerFactory.getLogger(TaskProxyController.class);
    private final TaskClient taskClient;

    public TaskProxyController(TaskClient taskClient) {
        this.taskClient = taskClient;
    }

    @GetMapping("/user/{userId}/tasks")
    public Flux<Task> getUserTasks(@PathVariable String userId) {
        logger.info("[Service A Controller] Received request for user tasks: {}", userId);
        return taskClient.getUserTasks(userId)
                .doOnComplete(() -> 
                    logger.info("[Service A Controller] Completed streaming tasks for user: {}", userId))
                .doOnError(error -> 
                    logger.error("[Service A Controller] Error streaming tasks: {}", error.getMessage()));
    }

    @GetMapping("/user/{userId}/tasks/list")
    public Mono<List<Task>> getUserTasksList(@PathVariable String userId) {
        logger.info("[Service A Controller] Received request for user tasks list: {}", userId);
        return taskClient.getUserTasks(userId)
                .collectList()
                .doOnSuccess(tasks -> 
                    logger.info("[Service A Controller] Collected {} tasks for user: {}", 
                        tasks.size(), userId));
    }

    @GetMapping("/user/{userId}/tasks/count")
    public Mono<Long> getUserTasksCount(@PathVariable String userId) {
        logger.info("[Service A Controller] Received request for user tasks count: {}", userId);
        return taskClient.getUserTasks(userId)
                .count()
                .doOnSuccess(count -> 
                    logger.info("[Service A Controller] User {} has {} tasks", userId, count));
    }

    @GetMapping("/user/{userId}/tasks/filter")
    public Flux<Task> getHighPriorityTasks(@PathVariable String userId) {
        logger.info("[Service A Controller] Filtering high priority tasks for user: {}", userId);
        return taskClient.getUserTasks(userId)
                .filter(task -> task.getPriority() == Task.TaskPriority.HIGH 
                    || task.getPriority() == Task.TaskPriority.CRITICAL)
                .doOnNext(task -> 
                    logger.debug("[Service A Controller] High priority task: {}", task.getTitle()));
    }

    @GetMapping("/health")
    public Mono<String> health() {
        return taskClient.checkServiceHealth()
                .map(serviceBHealth -> "Service A is running. Service B: " + serviceBHealth);
    }

}
