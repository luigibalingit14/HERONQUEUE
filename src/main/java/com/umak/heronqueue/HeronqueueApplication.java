package com.umak.heronqueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point ng HeronQueue. Ina-activate ng Spring Boot ang application
 * at awtomatikong hinahanap ang controllers, services, at repositories.
 */
@SpringBootApplication
public class HeronqueueApplication {

    public static void main(String[] args) {
        // Dito nagsisimula ang web server at ang buong queue system.
        SpringApplication.run(HeronqueueApplication.class, args);
    }

}
