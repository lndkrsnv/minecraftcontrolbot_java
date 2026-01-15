package com.lndkrsnv.minecraftcontrolbot.service;

import com.lndkrsnv.minecraftcontrolbot.config.StatusProperties;
import com.lndkrsnv.minecraftcontrolbot.status.StatusResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Service
public class StatusClient {
    private static final int TIMEOUT_SECONDS = 2;
    private final RestClient client;
    private final StatusProperties props;
    private final ObjectMapper objectMapper;

    public StatusClient(StatusProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));
        factory.setReadTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));
        
        this.client = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public StatusResponse fetchStatus(String serverId) {
        var server = props.servers().get(serverId);
        if (server == null) {
            throw new IllegalArgumentException("Unknown status serverId: " + serverId);
        }

        try {
            String raw = client.get()
                    .uri(server.url())
                    .retrieve()
                    .body(String.class);

            try {
                return objectMapper.readValue(raw, StatusResponse.class);
            } catch (Exception e) {
                assert raw != null;
                throw new IllegalStateException("Invalid status payload (first 200 chars): "
                        + raw.substring(0, Math.min(raw.length(), 200)), e);
            }
        } catch (org.springframework.web.client.ResourceAccessException e) {
            if (e.getCause() instanceof java.net.SocketTimeoutException) {
                throw new RuntimeException("Status сервер недоступен: превышено время ожидания (" + TIMEOUT_SECONDS + " сек)", e);
            }
            throw new RuntimeException("Status сервер недоступен", e);
        }
    }
}
