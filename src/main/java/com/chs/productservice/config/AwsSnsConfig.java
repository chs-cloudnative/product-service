package com.chs.productservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
@Profile("!local")
public class AwsSnsConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    /**
     * Create SNS client bean
     * Uses default credentials chain (IAM role from EC2 instance profile)
     */
    @Bean
    public SnsClient snsClient() {
        return SnsClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
