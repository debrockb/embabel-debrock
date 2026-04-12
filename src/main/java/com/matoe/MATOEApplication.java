package com.matoe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MATOEApplication {
    public static void main(String[] args) {
        SpringApplication.run(MATOEApplication.class, args);
    }
}
