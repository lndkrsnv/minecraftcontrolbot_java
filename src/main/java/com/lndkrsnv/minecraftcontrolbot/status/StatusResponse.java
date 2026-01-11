package com.lndkrsnv.minecraftcontrolbot.status;

public record StatusResponse(
        ServerInfo server,
        VersionInfo version,
        PlayersInfo players,
        String description
) {}

