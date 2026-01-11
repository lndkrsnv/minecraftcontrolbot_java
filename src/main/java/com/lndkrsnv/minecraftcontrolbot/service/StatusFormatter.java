package com.lndkrsnv.minecraftcontrolbot.service;

import com.lndkrsnv.minecraftcontrolbot.status.StatusResponse;
import org.springframework.stereotype.Component;

@Component
public class StatusFormatter {

    public String format(StatusResponse s) {
        String statusStr = "🟢 Онлайн";
        String version = (s.version() != null && s.version().name() != null) ? s.version().name() : "неизвестна";

        int online = s.players() != null ? s.players().online() : 0;
        Integer max = s.players() != null ? s.players().max() : null;

        StringBuilder sb = new StringBuilder();
        sb.append("Статус сервера: ").append(statusStr).append("\n");

        if (s.description() != null && !s.description().isBlank()) {
            sb.append("Описание: ").append(s.description()).append("\n");
        }

        sb.append("Версия: ").append(version).append("\n");

        if (max != null && max > 0) sb.append("Игроки: ").append(online).append("/").append(max).append("\n\n");
        else sb.append("Игроки: ").append(online).append("\n\n");

        if (s.server() != null) {
            sb.append("Latency: ").append(s.server().latency()).append(" ms").append("\n\n");
        }

        if (s.players() != null && s.players().sample() != null && !s.players().sample().isEmpty()) {
            for (var p : s.players().sample()) {
                sb.append(" • ").append(p.name()).append("\n");
            }
        } else {
            sb.append("Нет игроков онлайн");
        }

        return sb.toString().trim();
    }
}
