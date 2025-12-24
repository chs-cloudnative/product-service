package com.chs.webapp.controller;

import com.chs.webapp.dto.UserCreateRequest;
import com.chs.webapp.dto.UserResponse;
import com.chs.webapp.dto.UserUpdateRequest;
import com.chs.webapp.service.UserService;
import com.timgroup.statsd.StatsDClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final StatsDClient statsDClient;

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody UserCreateRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.user.post.count");
            log.info("POST /v1/user - Creating user with email: {}", request.getEmail());

            UserResponse userResponse = userService.createUser(request);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.user.post.time", duration);
            log.info("POST /v1/user - User created successfully - {}ms", duration);

            return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.user.post.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("POST /v1/user - Error creating user: {} - {}ms", e.getMessage(), duration, e);
            throw e;
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable UUID id, Authentication authentication) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.user.get.count");
            log.info("GET /v1/user/{} - Getting user info", id);

            String authenticatedEmail = authentication.getName();
            UserResponse userResponse = userService.getUserById(id);

            if (!userResponse.getEmail().equals(authenticatedEmail)) {
                statsDClient.incrementCounter("api.user.get.forbidden");
                log.warn("GET /v1/user/{} - Access denied for user: {}", id, authenticatedEmail);
                throw new IllegalArgumentException("Access denied: Users can only view their own information");
            }

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.user.get.time", duration);
            log.info("GET /v1/user/{} - User retrieved successfully - {}ms", id, duration);

            return ResponseEntity.ok(userResponse);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.user.get.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("GET /v1/user/{} - Error: {} - {}ms", id, e.getMessage(), duration, e);
            throw e;
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @Valid @RequestBody UserUpdateRequest request, Authentication authentication) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.user.put.count");
            log.info("PUT /v1/user/{} - Updating user", id);

            String authenticatedEmail = authentication.getName();
            UserResponse userResponse = userService.updateUser(id, request, authenticatedEmail);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.user.put.time", duration);
            log.info("PUT /v1/user/{} - User updated successfully - {}ms", id, duration);

            return ResponseEntity.ok(userResponse);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.user.put.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("PUT /v1/user/{} - Error: {} - {}ms", id, e.getMessage(), duration, e);
            throw e;
        }
    }

    @DeleteMapping("/self")
    public ResponseEntity<?> deleteCurrentUser(Authentication authentication) {
        long startTime = System.currentTimeMillis();

        try {
            statsDClient.incrementCounter("api.user.delete.count");
            String authenticatedEmail = authentication.getName();
            log.info("DELETE /v1/user/self - Deleting user: {}", authenticatedEmail);

            userService.deleteUserByEmail(authenticatedEmail);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("api.user.delete.time", duration);
            log.info("DELETE /v1/user/self - User deleted successfully - {}ms", duration);

            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            statsDClient.incrementCounter("api.user.delete.error");
            long duration = System.currentTimeMillis() - startTime;
            log.error("DELETE /v1/user/self - Error: {} - {}ms", e.getMessage(), duration, e);
            throw e;
        }
    }
}
