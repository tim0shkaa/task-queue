package com.taskqueue.serviceb.controller;

import com.taskqueue.serviceb.model.Task;
import com.taskqueue.serviceb.service.TaskService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/{userId}")
    public Flux<Task> getUserTasks(@PathVariable String userId) {
        return taskService.getUserTasks(userId);
    }

    @GetMapping("/health")
    public String health() {
        return "Service B is running";
    }

}
