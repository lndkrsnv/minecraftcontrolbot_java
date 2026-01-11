package com.lndkrsnv.minecraftcontrolbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "status")
public record StatusProperties(String url) {}
