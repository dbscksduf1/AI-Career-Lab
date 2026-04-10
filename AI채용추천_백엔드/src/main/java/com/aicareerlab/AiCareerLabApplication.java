package com.aicareerlab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiCareerLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCareerLabApplication.class, args);
    }
}
