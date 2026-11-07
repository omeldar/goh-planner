package com.omeldar.planner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;

/** {@code config/planner/client.json}: only the bridge settings. Projects live beside it. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    /**
     * First port to try for the loopback bridge; the next few are tried if it is taken.
     * Lumi uses 25599, Autopilot 25598, GoH 25597.
     */
    public int bridgePort = 25596;
    public String bridgeToken = "";

    public static Config get() {
        if (instance == null) load();
        return instance;
    }

    public static synchronized void load() {
        Config cfg = new Config();
        Path f = Store.dir().resolve("client.json");
        try {
            if (Files.exists(f)) {
                Config read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Config.class);
                if (read != null) cfg = read;
            }
        } catch (Exception e) {
            PlannerClient.LOG.warn("could not read {}, using defaults", f, e);
        }
        if (cfg.bridgeToken == null || cfg.bridgeToken.isBlank()) {
            byte[] b = new byte[24];
            new SecureRandom().nextBytes(b);
            cfg.bridgeToken = HexFormat.of().formatHex(b);
        }
        instance = cfg;
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(cfg), StandardCharsets.UTF_8);
        } catch (Exception e) {
            PlannerClient.LOG.warn("could not save {}", f, e);
        }
    }
}
