package com.chs.productservice.service;

import com.chs.productservice.entity.EmailVerification;
import com.chs.productservice.entity.User;
import com.chs.productservice.repository.EmailVerificationRepository;
import com.chs.productservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)  // ← 改成 optional
    private SnsClient snsClient;

    @Value("${aws.sns.topic-arn}")
    private String snsTopicArn;

    // 手動建立 constructor（移除 @RequiredArgsConstructor）
    public EmailVerificationService(
            EmailVerificationRepository verificationRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Send verification email via SNS
     */
    @Transactional
    public void sendVerificationEmail(User user) {
        log.info("Sending verification email to: {}", user.getEmail());

        // Skip email sending if SNS is not configured (test/local environment)
        if (snsClient == null || snsTopicArn == null || snsTopicArn.trim().isEmpty()) {
            log.warn("SNS not configured, skipping email verification for: {}", user.getEmail());
            return;
        }

        boolean hasValidToken = verificationRepository
                .existsByEmailAndVerifiedFalseAndExpiresAtAfter(
                        user.getEmail(),
                        LocalDateTime.now()
                );
        if (hasValidToken) {
            log.warn("Valid verification token already exists for: {}", user.getEmail());
            return;
        }

        // Generate verification token
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(3);

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

        EmailVerification verification = verificationRepository
                .findByEmailAndToken(email, token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (verification.getVerified()) {
            throw new IllegalArgumentException("Email already verified");
        }

        if (verification.isExpired()) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        verification.setVerified(true);
        verificationRepository.save(verification);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setVerified(true);
        userRepository.save(user);

        log.info("Email verified successfully for: {}", email);
    }

    /**
     * Cleanup expired tokens
     */
    @Transactional
    public void cleanupExpiredTokens() {
        verificationRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        log.info("Cleaned up expired verification tokens");
    }
}
