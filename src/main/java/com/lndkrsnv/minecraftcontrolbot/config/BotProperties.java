package com.lndkrsnv.minecraftcontrolbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot")
public record BotProperties(
        String token,
        String username,
        long superUser,
        String authorizedUsers
) {}
