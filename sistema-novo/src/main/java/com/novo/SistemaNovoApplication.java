package com.novo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SistemaNovoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SistemaNovoApplication.class, args);
    }
}
