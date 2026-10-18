package com.example.qa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QaApplication {
    public static void main(String[] args) {
        SpringApplication.run(QaApplication.class, args);
    }
}
