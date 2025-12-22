package com.chs.webapp.service;

import com.chs.webapp.entity.EmailVerification;
import com.chs.webapp.entity.User;
import com.chs.webapp.repository.EmailVerificationRepository;
import com.chs.webapp.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sns.topic-arn}")
    private String snsTopicArn;

    /**
     * Send verification email via SNS
     */
    @Transactional
    public void sendVerificationEmail(User user) {
        log.info("Sending verification email to: {}", user.getEmail());

        // Skip email sending if SNS is not configured (test environment)
        if (snsTopicArn == null || snsTopicArn.trim().isEmpty()) {
            log.warn("SNS Topic ARN not configured, skipping email verification");
            return;
        }

        boolean hasValidToken = verificationRepository
                .existsByEmailAndVerifiedFalseAndExpiresAtAfter(
                        user.getEmail(),
                        LocalDateTime.now()
                );
        if (hasValidToken) { // Check if a valid token already exists (prevent duplicate emails)
            log.warn("Valid verification token already exists for: {}", user.getEmail());
            return;
        }

        // Generate verification token
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(1); // 1 minute expiry

        // Save verification record
        EmailVerification verification = EmailVerification.builder()
                .email(user.getEmail())
                .token(token)
                .expiresAt(expiresAt)
                .verified(false)
                .build();

        verificationRepository.save(verification);
        log.info("Verification token created for: {}, expires at: {}", user.getEmail(), expiresAt);

        // Publish message to SNS
        try {
            Map<String, String> message = new HashMap<>();
            message.put("email", user.getEmail());
            message.put("token", token);
            message.put("firstName", user.getFirstName());

            String messageJson = objectMapper.writeValueAsString(message);

            PublishRequest request = PublishRequest.builder()
                    .topicArn(snsTopicArn)
                    .message(messageJson)
                    .build();

            PublishResponse response = snsClient.publish(request);
            log.info("SNS message published successfully. MessageId: {}", response.messageId());

        } catch (Exception e) {
            log.error("Failed to publish SNS message for: {}", user.getEmail(), e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    /**
     * Verify email with token
     */
    @Transactional
    public void verifyEmail(String email, String token) {
        log.info("Verifying email: {} with token: {}", email, token);

        // Find verification record
        EmailVerification verification = verificationRepository
                .findByEmailAndToken(email, token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        // Check if already verified
        if (verification.getVerified()) {
            throw new IllegalArgumentException("Email already verified");
        }

        // Check if expired
        if (verification.isExpired()) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        // Mark verification as used
        verification.setVerified(true);
        verificationRepository.save(verification);

        // Update user as verified
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setVerified(true);
        userRepository.save(user);

        log.info("Email verified successfully for: {}", email);
    }

    /**
     * Cleanup expired tokens (can be called by scheduled task)
     */
    @Transactional
    public void cleanupExpiredTokens() {
        verificationRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Cleaned up expired verification tokens");
    }
}
