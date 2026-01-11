package com.lndkrsnv.minecraftcontrolbot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({BotProperties.class, RconProperties.class, StatusProperties.class})
public class PropertiesConfig {}
