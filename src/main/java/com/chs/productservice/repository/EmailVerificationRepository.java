package com.chs.productservice.repository;

import com.chs.productservice.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

    /**
     * Find verification by email and token
     * Used when user clicks verification link
     */
    Optional<EmailVerification> findByEmailAndToken(String email, String token);

    /**
     * Find the most recent verification for an email
     * Used to check if verification was recently sent
     */
    Optional<EmailVerification> findFirstByEmailOrderByCreatedAtDesc(String email);

    /**
     * Check if a valid (unexpired, unverified) token exists for email
     * Used to prevent sending duplicate emails
     */
    boolean existsByEmailAndVerifiedFalseAndExpiresAtAfter(String email, LocalDateTime now);

    /**
     * Delete old expired tokens (cleanup)
     * Can be used in a scheduled task
     */
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
