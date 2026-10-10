package edu.boisestate.cs408.trenchroster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the web server for the CS408 deployment checkpoint. */
@SpringBootApplication
public class TrenchRosterApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrenchRosterApplication.class, args);
    }
}
