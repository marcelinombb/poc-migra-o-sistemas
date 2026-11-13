package br.com.legado;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SistemaLegadoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SistemaLegadoApplication.class, args);
    }
}
