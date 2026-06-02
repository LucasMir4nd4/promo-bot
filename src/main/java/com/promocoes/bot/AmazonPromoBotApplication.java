package com.promocoes.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AmazonPromoBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmazonPromoBotApplication.class, args);
    }
}
