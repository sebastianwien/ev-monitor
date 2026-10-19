package com.evmonitor.infrastructure.email;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/** Legt {@link LoggingMailSender} um jeden {@link JavaMailSender}, damit jeder Versand im Log steht. */
@Configuration
public class MailLoggingConfig {

    @Bean
    static BeanPostProcessor loggingMailSenderPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof JavaMailSender sender && !(bean instanceof LoggingMailSender)) {
                    return new LoggingMailSender(sender);
                }
                return bean;
            }
        };
    }
}
