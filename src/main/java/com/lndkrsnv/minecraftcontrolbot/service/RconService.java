package com.lndkrsnv.minecraftcontrolbot.service;

import com.lndkrsnv.minecraftcontrolbot.config.RconProperties;
import org.glavo.rcon.AuthenticationException;
import org.glavo.rcon.Rcon;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class RconService {
    private static final int TIMEOUT_SECONDS = 2;
    private final RconProperties props;

    public RconService(RconProperties props) {
        this.props = props;
    }

    public void command(String serverId, String cmd) {
        var server = props.servers().get(serverId);
        if (server == null) {
            throw new IllegalArgumentException("Unknown serverId: " + serverId);
        }
        
        try {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try (Rcon rcon = new Rcon(server.host(), server.port(), server.password())) {
                    rcon.command(cmd);
                } catch (IOException | AuthenticationException e) {
                    throw new RuntimeException(e);
                }
            });
            
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("RCON сервер недоступен: превышено время ожидания (" + TIMEOUT_SECONDS + " сек)", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Ошибка выполнения RCON команды", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RCON команда прервана", e);
        }
    }
}
