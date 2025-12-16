package com.taskqueue.servicea.client;

import com.taskqueue.servicea.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
public class TaskClient {

    private static final Logger logger = LoggerFactory.getLogger(TaskClient.class);
    private final WebClient webClient;

    public TaskClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Flux<Task> getUserTasks(String userId) {
        logger.info("[Service A] Fetching tasks for user: {}", userId);

        return webClient.get()
                .uri("/api/tasks/{userId}", userId)
                .retrieve()
                .bodyToFlux(Task.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(10))
                        .doBeforeRetry(retrySignal -> 
                            logger.warn("[Service A] Retrying request, attempt: {}", 
                                retrySignal.totalRetries() + 1))
                )
                .doOnError(WebClientResponseException.class, ex -> 
                    logger.error("[Service A] HTTP error: {} - {}", 
                        ex.getStatusCode(), ex.getResponseBodyAsString())
                )
                .doOnError(Exception.class, ex -> 
                    logger.error("[Service A] Error fetching tasks: {}", ex.getMessage())
                )
                .onErrorResume(error -> {
                    logger.error("[Service A] Failed to fetch tasks after retries: {}", 
                        error.getMessage());
                    return Flux.empty();
                });
    }

    public Mono<String> checkServiceHealth() {
        return webClient.get()
                .uri("/api/tasks/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .doOnSuccess(response -> 
                    logger.info("[Service A] Service B health check: {}", response))
                .onErrorResume(error -> {
                    logger.error("[Service A] Service B health check failed: {}", 
                        error.getMessage());
                    return Mono.just("Service B unavailable");
                });
    }

}
