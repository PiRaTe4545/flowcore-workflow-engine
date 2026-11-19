package com.flowcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlowCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowCoreApplication.class, args);
    }
}
