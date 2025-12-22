package com.chs.webapp.config;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public StatsDClient statsDClient() {
        return new NonBlockingStatsDClient(
            "productservice",       // prefix
            "localhost",     // hostname
            8125             // port
        );
    }
}
