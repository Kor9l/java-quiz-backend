package com.korl.javaquiz.config;

import com.korl.javaquiz.practice.SandboxLimits;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PracticeConfig {

    @Bean
    public SandboxLimits sandboxLimits(AppProperties properties) {
        AppProperties.Practice practice = properties.getPractice();
        return new SandboxLimits(
                practice.getQueryTimeoutSeconds(),
                practice.getMaxRows(),
                practice.getMaxSqlLength(),
                practice.getPreviewRows());
    }
}
