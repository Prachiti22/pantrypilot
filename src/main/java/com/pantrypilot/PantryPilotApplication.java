package com.pantrypilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * One Spring Boot server split into three modules, one per team member:
 * {@code ai} (Rohit), {@code core} (Prachiti) and {@code insights} (Vishvak).
 * The modules only talk through the types in {@code shared}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PantryPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PantryPilotApplication.class, args);
    }
}
