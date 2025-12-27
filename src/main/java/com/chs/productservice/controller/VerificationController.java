package com.chs.productservice.controller;

import com.chs.productservice.service.EmailVerificationService;
import com.timgroup.statsd.StatsDClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
@Slf4j
public class VerificationController {

    private final EmailVerificationService emailVerificationService;
    private final StatsDClient statsDClient;

    /**
     * Verify user email with token
     * GET /v1/user/verify?email=user@example.com&token=uuid-token
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(
            @RequestParam String email,
            @RequestParam String token
    ) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.user.verify.count");
            log.info("GET /v1/user/verify - Verifying email: {}", email);

            // Verify email with token
            emailVerificationService.verifyEmail(email, token);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.user.verify.time", duration);
            statsDClient.incrementCounter("api.user.verify.success");
            log.info("GET /v1/user/verify - Email verified successfully: {} - {}ms", email, duration);

            // Return success response
            Map<String, String> response = new HashMap<>();
            response.put("message", "Email verified successfully");
            response.put("email", email);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            statsDClient.incrementCounter("api.user.verify.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("GET /v1/user/verify - Verification failed: {} - {}ms", e.getMessage(), duration);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.user.verify.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("GET /v1/user/verify - Error: {} - {}ms", e.getMessage(), duration, e);

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Verification failed");

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
