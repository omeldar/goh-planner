package com.omeldar.planner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The projects of the world the player is in. Each world (single player save, or multiplayer
 * server address) has its own file under {@code config/planner/}, because a castle planned on
 * one server has nothing to do with another. Client thread only.
 */
public final class Store {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String worldKey;
    private static List<Project> projects = new ArrayList<>();
    /** The project commands and Lumi act on when none is named: the last one opened. */
    private static String current = "";

    private Store() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("planner");
    }

    /** Which world this is, as a file-safe name. Null when not in a world. */
    static String keyFor(MinecraftClient mc) {
        if (mc.world == null) return null;
        String raw;
        if (mc.isInSingleplayer() && mc.getServer() != null) {
            raw = "sp-" + mc.getServer().getSaveProperties().getLevelName();
        } else {
            var entry = mc.getCurrentServerEntry();
            raw = "mp-" + (entry == null ? "unknown" : entry.address);
        }
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    /** Make sure the loaded projects belong to the world the player is in now. */
    public static boolean ready() {
        String key = keyFor(MinecraftClient.getInstance());
        if (key == null) return false;
        if (!key.equals(worldKey)) load(key);
        return true;
    }

    public static void forget() {
        worldKey = null;
        projects = new ArrayList<>();
        current = "";
    }

    public static List<Project> all() {
        return projects;
    }

    public static Project find(String name) {
        if (name == null) return null;
        for (Project p : projects) if (p.name.equalsIgnoreCase(name.trim())) return p;
        return null;
    }

    /** The named project, or the current one when the name is blank. */
    public static Project pick(String name) {
        if (name != null && !name.isBlank()) return find(name);
        Project c = find(current);
        return c != null ? c : projects.size() == 1 ? projects.get(0) : null;
    }

    public static Project current() {
        return find(current);
    }

    public static void setCurrent(Project p) {
        current = p == null ? "" : p.name;
    }

    /** Null on success, otherwise why not. */
    public static String create(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "a project needs a name";
        if (n.length() > 40) return "names are at most 40 characters";
        if (find(n) != null) return "there is already a project called \"" + n + "\"";
        Project p = new Project();
        p.name = n;
        p.created = LocalDate.now().toString();
        projects.add(p);
        current = n;
        save();
        return null;
    }

    public static void delete(Project p) {
        projects.remove(p);
        if (p.name.equalsIgnoreCase(current)) current = "";
        save();
    }

    public static String rename(Project p, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "a project needs a name";
        Project other = find(n);
        if (other != null && other != p) return "there is already a project called \"" + n + "\"";
        boolean wasCurrent = p.name.equalsIgnoreCase(current);
        p.name = n;
        if (wasCurrent) current = n;
        save();
        return null;
    }

    private static Path file() {
        return dir().resolve(worldKey + ".json");
    }

    private static void load(String key) {
        worldKey = key;
        projects = new ArrayList<>();
        current = "";
        try {
            Path f = file();
            if (Files.exists(f)) {
                List<Project> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        new TypeToken<List<Project>>() {}.getType());
                if (read != null) projects = new ArrayList<>(read);
            }
        } catch (Exception e) {
            PlannerClient.LOG.warn("could not read projects for {}", key, e);
            // Keep going with an empty list but never save over a file we could not read.
            worldKey = null;
        }
    }

    public static void save() {
        if (worldKey == null) return;
        try {
            Files.createDirectories(dir());
            Path tmp = dir().resolve(worldKey + ".json.tmp");
            Files.writeString(tmp, GSON.toJson(projects), StandardCharsets.UTF_8);
            Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            PlannerClient.LOG.warn("could not save projects", e);
        }
    }
}
