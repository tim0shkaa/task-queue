package com.taskqueue.servicea.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
public class LoggingWebFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String requestId = exchange.getRequest().getId();
        String method = exchange.getRequest().getMethod().toString();
        String path = exchange.getRequest().getURI().getPath();

        logger.info("[Service A] [{}] Incoming request: {} {} at {}", 
            requestId, method, path, LocalDateTime.now());

        return chain.filter(exchange)
                .doOnSuccess(aVoid -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = exchange.getResponse().getStatusCode() != null 
                        ? exchange.getResponse().getStatusCode().value() 
                        : 0;
                    logger.info("[Service A] [{}] Response: {} - {} ms - Status: {}", 
                        requestId, path, duration, statusCode);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logger.error("[Service A] [{}] Error: {} - {} ms - Error: {}", 
                        requestId, path, duration, error.getMessage());
                });
    }

}
