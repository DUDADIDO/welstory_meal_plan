package com.ssafy.welstory;

import com.ssafy.welstory.config.AdminProperties;
import com.ssafy.welstory.config.WelstoryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({WelstoryProperties.class, AdminProperties.class})
@SpringBootApplication
public class WelstoryMealApplication {
    public static void main(String[] args) {
        SpringApplication.run(WelstoryMealApplication.class, args);
    }
}
