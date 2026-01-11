package com.lndkrsnv.minecraftcontrolbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Map;

@ConfigurationProperties(prefix = "rcon")
public record RconProperties(
        String defaultServer,
        Map<String, Server> servers
) {
    public record Server(String host, int port, String password) {}
}