package com.lndkrsnv.minecraftcontrolbot.status;

import java.util.List;

public record PlayersInfo(
        int max,
        int online,
        List<PlayerSample> sample
) {}
