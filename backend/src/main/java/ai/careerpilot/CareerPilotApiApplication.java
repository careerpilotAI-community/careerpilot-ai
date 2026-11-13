package ai.careerpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CareerPilotApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CareerPilotApiApplication.class, args);
    }
}