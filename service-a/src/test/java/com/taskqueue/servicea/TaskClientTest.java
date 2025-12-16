package com.taskqueue.servicea;

import com.taskqueue.servicea.client.TaskClient;
import com.taskqueue.servicea.model.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest
class TaskClientTest {

    @Autowired
    private TaskClient taskClient;

    @Test
    void getUserTasks_HandlesEmptyResponse() {
        Flux<Task> tasks = taskClient.getUserTasks("nonexistent");

        StepVerifier.create(tasks)
                .expectComplete()
                .verify();
    }

    @Test
    void checkServiceHealth_ReturnsStatus() {
        StepVerifier.create(taskClient.checkServiceHealth())
                .expectNextMatches(health -> health != null && !health.isEmpty())
                .verifyComplete();
    }

}
