package com.ssafy.welstory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(String username, String password) {
    public AdminProperties {
        username = username == null ? "" : username.trim();
        password = password == null ? "" : password;
    }

    public boolean configured() {
        return !username.isBlank() && !password.isBlank();
    }
}
