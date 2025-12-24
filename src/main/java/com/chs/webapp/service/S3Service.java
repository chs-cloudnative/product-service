package com.chs.webapp.service;

import com.timgroup.statsd.StatsDClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class S3Service {

    @Autowired(required = false)  // ← S3Client 變成 optional
    private S3Client s3Client;

    private final String bucketName;
    private final StatsDClient statsDClient;

    // Constructor 不包含 s3Client
    public S3Service(@Value("${aws.s3.bucket-name}") String bucketName,
                     StatsDClient statsDClient) {
        this.bucketName = bucketName;
        this.statsDClient = statsDClient;
    }

    public String uploadFile(MultipartFile file, UUID userId, UUID productId) {
        // 本地測試時跳過 S3
        if (s3Client == null || bucketName == null || bucketName.trim().isEmpty()) {
            log.warn("S3 not configured, skipping file upload for local testing");
            // 返回一個假的 S3 key 用於測試
            return String.format("local-test/%s/%s/%s", userId, productId, file.getOriginalFilename());
        }

        long startTime = System.currentTimeMillis();

        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String originalFilename = file.getOriginalFilename();
            String s3Key = String.format("%s/%s/%s-%s", userId, productId, timestamp, originalFilename);

            log.info("Uploading file to S3: bucket={}, key={}", bucketName, s3Key);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("s3.upload.time", duration);
            statsDClient.incrementCounter("s3.upload.success");

            log.info("File uploaded successfully to S3: {} - {}ms", s3Key, duration);
            return s3Key;

        } catch (S3Exception e) {
            statsDClient.incrementCounter("s3.upload.error");
            log.error("Error uploading file to S3: {}", e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        } catch (IOException e) {
            statsDClient.incrementCounter("s3.upload.error");
            log.error("Error reading file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String s3Key) {
        // 本地測試時跳過 S3
        if (s3Client == null || bucketName == null || bucketName.trim().isEmpty()) {
            log.warn("S3 not configured, skipping file delete for local testing");
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            log.info("Deleting file from S3: bucket={}, key={}", bucketName, s3Key);

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);

            long duration = System.currentTimeMillis() - startTime;
            statsDClient.recordExecutionTime("s3.delete.time", duration);
            statsDClient.incrementCounter("s3.delete.success");

            log.info("File deleted successfully from S3: {} - {}ms", s3Key, duration);

        } catch (S3Exception e) {
            statsDClient.incrementCounter("s3.delete.error");
            log.error("Error deleting file from S3: {}", e.awsErrorDetails().errorMessage(), e);
            throw new RuntimeException("Failed to delete file from S3: " + e.getMessage(), e);
        }
    }

    public boolean fileExists(String s3Key) {
        // 本地測試時假設文件存在
        if (s3Client == null || bucketName == null || bucketName.trim().isEmpty()) {
            log.warn("S3 not configured, returning true for local testing");
            return true;
        }

        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.headObject(headObjectRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.error("Error checking file existence in S3: {}", e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Failed to check file existence: " + e.getMessage(), e);
        }
    }
}
