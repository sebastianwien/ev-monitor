package com.evmonitor.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final DemoAccountInterceptor demoAccountInterceptor;

    public WebMvcConfig(DemoAccountInterceptor demoAccountInterceptor) {
        this.demoAccountInterceptor = demoAccountInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(demoAccountInterceptor).addPathPatterns("/api/**");
    }
}
