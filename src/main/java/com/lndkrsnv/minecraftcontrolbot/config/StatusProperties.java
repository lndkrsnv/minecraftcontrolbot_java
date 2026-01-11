package com.lndkrsnv.minecraftcontrolbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "status")
public record StatusProperties(
        Map<String, Server> servers
) {
    public record Server(String url) {}
}
