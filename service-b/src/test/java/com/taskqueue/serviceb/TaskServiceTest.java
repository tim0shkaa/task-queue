package com.taskqueue.serviceb;

import com.taskqueue.serviceb.model.Task;
import com.taskqueue.serviceb.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TaskServiceTest {

    @Autowired
    private TaskService taskService;

    @Test
    void getUserTasks_ReturnsFlux() {
        Flux<Task> tasks = taskService.getUserTasks("user1");

        StepVerifier.create(tasks)
                .expectNextMatches(task -> 
                    task.getUserId().equals("user1") && task.getId() != null)
                .expectNextCount(0)
                .thenConsumeWhile(task -> task.getUserId().equals("user1"))
                .verifyComplete();
    }

    @Test
    void getUserTasks_FiltersCorrectly() {
        Flux<Task> tasks = taskService.getUserTasks("user1");

        tasks.collectList().subscribe(taskList -> {
            assertTrue(taskList.size() > 0);
            taskList.forEach(task -> {
                assertEquals("user1", task.getUserId());
                assertNotEquals(Task.TaskStatus.CANCELLED, task.getStatus());
                assertTrue(task.getEstimatedHours() > 0);
            });
        });
    }

}
