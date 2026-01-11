package com.lndkrsnv.minecraftcontrolbot.service;

import com.lndkrsnv.minecraftcontrolbot.config.StatusProperties;
import com.lndkrsnv.minecraftcontrolbot.status.StatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Service
public class StatusClient {
    private final RestClient client = RestClient.create();
    private final StatusProperties props;
    private final ObjectMapper objectMapper;

    public StatusClient(StatusProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public StatusResponse fetchStatus() {
        String raw = client.get()
                .uri(props.url())
                .retrieve()
                .body(String.class);

        try {
            return objectMapper.readValue(raw, StatusResponse.class);
        } catch (Exception e) {
            assert raw != null;
            throw new IllegalStateException("Invalid status payload (first 200 chars): "
                    + raw.substring(0, Math.min(raw.length(), 200)), e);
        }
    }
}
