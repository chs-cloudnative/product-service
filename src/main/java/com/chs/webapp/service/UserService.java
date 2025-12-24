package com.chs.webapp.service;

import com.chs.webapp.dto.UserCreateRequest;
import com.chs.webapp.dto.UserResponse;
import com.chs.webapp.dto.UserUpdateRequest;
import com.chs.webapp.entity.User;
import com.chs.webapp.repository.UserRepository;
import com.timgroup.statsd.StatsDClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StatsDClient statsDClient;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        log.info("Creating user with email: {}", request.getEmail());

        long dbStartTime = System.currentTimeMillis();
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("User with email " + request.getEmail() + " already exists");
        }
        statsDClient.recordExecutionTime("db.user.existsByEmail.time", System.currentTimeMillis() - dbStartTime);

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

        dbStartTime = System.currentTimeMillis();
        User savedUser = userRepository.saveAndFlush(user);
        statsDClient.recordExecutionTime("db.user.save.time", System.currentTimeMillis() - dbStartTime);

        dbStartTime = System.currentTimeMillis();
        User refreshedUser = userRepository.findById(savedUser.getId()).orElse(savedUser);
        statsDClient.recordExecutionTime("db.user.findById.time", System.currentTimeMillis() - dbStartTime);

        log.info("User created successfully with ID: {}", savedUser.getId());

        // Send verification email via SNS
        long snsStartTime = System.currentTimeMillis();
        try {
            emailVerificationService.sendVerificationEmail(refreshedUser);
            statsDClient.recordExecutionTime("sns.verification.send.time", System.currentTimeMillis() - snsStartTime);
            statsDClient.incrementCounter("sns.verification.send.success");
            log.info("Verification email sent for user: {}", refreshedUser.getEmail());
        } catch (Exception e) {
            statsDClient.recordExecutionTime("sns.verification.send.time", System.currentTimeMillis() - snsStartTime);
            statsDClient.incrementCounter("sns.verification.send.failure");
            log.error("Failed to send verification email for: {}", refreshedUser.getEmail(), e);
            // Don't fail user creation if email fails, User can request resend verification email later
        }

        return mapToResponse(refreshedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        long dbStartTime = System.currentTimeMillis();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        statsDClient.recordExecutionTime("db.user.findById.time", System.currentTimeMillis() - dbStartTime);

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        long dbStartTime = System.currentTimeMillis();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
        statsDClient.recordExecutionTime("db.user.findByEmail.time", System.currentTimeMillis() - dbStartTime);

        return user;
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UserUpdateRequest request, String authenticatedEmail) {
        long dbStartTime = System.currentTimeMillis();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        statsDClient.recordExecutionTime("db.user.findById.time", System.currentTimeMillis() - dbStartTime);

        if (!user.getEmail().equals(authenticatedEmail)) {
            throw new IllegalArgumentException("Users can only update their own account information");
        }

        boolean updated = false;
        if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) {
            user.setFirstName(request.getFirstName().trim());
            updated = true;
        }
        if (request.getLastName() != null && !request.getLastName().trim().isEmpty()) {
            user.setLastName(request.getLastName().trim());
            updated = true;
        }
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            updated = true;
        }
        if (!updated) {
            throw new IllegalArgumentException("No valid fields provided for update");
        }

        dbStartTime = System.currentTimeMillis();
        User savedUser = userRepository.saveAndFlush(user);
        statsDClient.recordExecutionTime("db.user.update.time", System.currentTimeMillis() - dbStartTime);

        dbStartTime = System.currentTimeMillis();
        User refreshedUser = userRepository.findById(savedUser.getId()).orElse(savedUser);
        statsDClient.recordExecutionTime("db.user.findById.time", System.currentTimeMillis() - dbStartTime);

        log.info("User updated successfully with ID: {}", savedUser.getId());
        return mapToResponse(refreshedUser);
    }

    @Transactional
    public void deleteUserByEmail(String authenticatedEmail) {
        long dbStartTime = System.currentTimeMillis();
        User user = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + authenticatedEmail));
        statsDClient.recordExecutionTime("db.user.findByEmail.time", System.currentTimeMillis() - dbStartTime);

        dbStartTime = System.currentTimeMillis();
        userRepository.delete(user);
        statsDClient.recordExecutionTime("db.user.delete.time", System.currentTimeMillis() - dbStartTime);

        log.info("User deleted successfully: {}", authenticatedEmail);
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .accountCreated(user.getAccountCreated())
                .accountUpdated(user.getAccountUpdated())
                .verified(user.getVerified())
                .build();
    }
}
