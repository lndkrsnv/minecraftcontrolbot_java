package com.lndkrsnv.minecraftcontrolbot.service;

import com.lndkrsnv.minecraftcontrolbot.config.RconProperties;
import org.glavo.rcon.AuthenticationException;
import org.glavo.rcon.Rcon;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class RconService {
    private final RconProperties props;

    public RconService(RconProperties props) {
        this.props = props;
    }

    public void command(String serverId, String cmd) {
        var server = props.servers().get(serverId);
        if (server == null) {
            throw new IllegalArgumentException("Unknown serverId: " + serverId);
        }
        try (Rcon rcon = new Rcon(server.host(), server.port(), server.password())) {
            rcon.command(cmd);
        } catch (IOException | AuthenticationException e) {
            throw new RuntimeException(e);
        }
    }
}
