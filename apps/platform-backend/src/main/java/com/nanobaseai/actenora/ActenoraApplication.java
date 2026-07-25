package com.nanobaseai.actenora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Actenora modular monolith entrypoint.
 * Bounded contexts live as sibling packages under {@code com.nanobaseai.actenora}.
 */
@Modulithic(
        sharedModules = "sharedkernel",
        useFullyQualifiedModuleNames = false
)
@SpringBootApplication
public class ActenoraApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActenoraApplication.class, args);
    }
}
