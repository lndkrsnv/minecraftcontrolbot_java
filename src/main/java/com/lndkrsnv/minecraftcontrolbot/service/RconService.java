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

    public synchronized void command(String cmd) {
        try (Rcon rcon = new Rcon(props.host(), props.port(), props.password())) {
            rcon.command(cmd);
        } catch (IOException | AuthenticationException e) {
            throw new RuntimeException(e);
        }
    }
}
