package com.lndkrsnv.minecraftcontrolbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rcon")
public record RconProperties(
        String host,
        int port,
        String password
) {}
