package com.chs.productservice.controller;

import com.timgroup.statsd.StatsDClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final StatsDClient statsDClient;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        long startTime = System.currentTimeMillis();

        try {
            // Increment API call counter
            statsDClient.incrementCounter("api.health.count");

            log.info("Health check endpoint called");

            Map<String, String> response = Map.of(
                "status", "OK",
                "message", "Application is running"
            );

            // Record response time
            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.health.time", duration);

            log.info("Health check completed in {}ms", duration);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.health.error");
            log.error("Health check failed: {}", e.getMessage(), e);
            throw e;
        }
    }
}
