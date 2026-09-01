package com.korl.javaquiz.config;

import com.korl.javaquiz.practice.JavaLimits;
import com.korl.javaquiz.practice.SandboxLimits;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class PracticeConfig {

    @Produces
    @Singleton
    public SandboxLimits sandboxLimits(AppConfig config) {
        AppConfig.Practice practice = config.practice();
        return new SandboxLimits(
                practice.queryTimeoutSeconds(),
                practice.maxRows(),
                practice.maxSqlLength(),
                practice.previewRows());
    }

    @Produces
    @Singleton
    public JavaLimits javaLimits(AppConfig config) {
        AppConfig.Practice.Java java = config.practice().java();
        return new JavaLimits(java.runTimeoutSeconds(), java.maxSourceLength(), java.maxOutputBytes());
    }
}
