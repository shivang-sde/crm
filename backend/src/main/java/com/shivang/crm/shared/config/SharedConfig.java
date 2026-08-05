package com.shivang.crm.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SharedConfig {

    // Use Spring Boot's auto-configured ObjectMapper instead of providing
    // a primary ObjectMapper here. Avoid altering global JSON behavior to
    // satisfy standalone tests. Keep only small Jackson customization in
    // boot auto-configuration where necessary.

    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
