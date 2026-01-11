package com.lndkrsnv.minecraftcontrolbot.status;

public record ServerInfo(
        String targethostname,
        String hostname,
        String ipaddress,
        int port,
        int queryport,
        int latency
) {}
