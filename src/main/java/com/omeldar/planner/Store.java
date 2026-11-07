package com.omeldar.planner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Every project the player has, from every world and server, in one list.
 *
 * <p>One list rather than one per world on purpose: a build is often planned in a creative copy
 * of the world and then gathered on the server, so the plan has to travel. Each project still
 * remembers where it was made, and the list shows it. Kept in {@code config/planner/projects.json}.
 * Client thread only.
 */
public final class Store {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "projects.json";

    private static List<Project> projects;
    /** False when the file exists but could not be read, so it is never saved over. */
    private static boolean writable = true;
    /** The project commands and Lumi act on when none is named: the last one opened. */
    private static String current = "";

    private Store() {}

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("planner");
    }

    /** Where the player is now, for labelling a new project: "Server goh.example.net". */
    public static String worldLabel() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return "";
        if (mc.isInSingleplayer() && mc.getServer() != null) {
            return "World " + mc.getServer().getSaveProperties().getLevelName();
        }
        var entry = mc.getCurrentServerEntry();
        return "Server " + (entry == null ? "unknown" : entry.address);
    }

    /** Load the list on first use. Always true now that projects do not belong to one world. */
    public static boolean ready() {
        if (projects == null) load();
        return true;
    }

    public static List<Project> all() {
        ready();
        return projects;
    }

    public static Project find(String name) {
        if (name == null) return null;
        for (Project p : all()) if (p.name.equalsIgnoreCase(name.trim())) return p;
        return null;
    }

    /** The named project, or the current one when the name is blank. */
    public static Project pick(String name) {
        if (name != null && !name.isBlank()) return find(name);
        Project c = find(current);
        return c != null ? c : all().size() == 1 ? all().get(0) : null;
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
        p.world = worldLabel();
        p.created = LocalDate.now().toString();
        all().add(p);
        current = n;
        save();
        return null;
    }

    /** A copy under a new name, for planning a variant without losing the original. */
    public static String duplicate(Project p, String name) {
        String failed = create(name);
        if (failed != null) return failed;
        Project copy = current();
        copy.notes = p.notes;
        for (Project.Entry e : p.entries) {
            Project.Entry c = new Project.Entry();
            c.item = e.item;
            c.target = e.target;
            c.unit = e.unit;
            c.ratePerHour = e.ratePerHour;
            copy.entries.add(c);
        }
        save();
        return null;
    }

    public static void delete(Project p) {
        all().remove(p);
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

    private static void load() {
        projects = new ArrayList<>();
        Path f = dir().resolve(FILE);
        try {
            if (Files.exists(f)) {
                List<Project> read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                        new TypeToken<List<Project>>() {}.getType());
                if (read != null) projects.addAll(read);
            }
        } catch (Exception e) {
            PlannerClient.LOG.warn("could not read {}; not saving over it", f, e);
            writable = false;
            return;
        }
        migrateOldFiles();
    }

    /**
     * 0.1.0 kept one file per world ({@code sp-<world>.json}, {@code mp-<address>.json}). Fold
     * them into the shared list, label each project with where it came from, and rename the old
     * file so it is not folded in twice.
     */
    private static void migrateOldFiles() {
        int moved = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir(), "{sp,mp}-*.json")) {
            for (Path old : files) {
                String base = old.getFileName().toString().replaceFirst("\\.json$", "");
                String label = (base.startsWith("sp-") ? "World " : "Server ") + base.substring(3);
                List<Project> read = GSON.fromJson(Files.readString(old, StandardCharsets.UTF_8),
                        new TypeToken<List<Project>>() {}.getType());
                if (read != null) {
                    for (Project p : read) {
                        if (p.world == null || p.world.isBlank()) p.world = label;
                        String name = p.name;
                        for (int i = 2; find(p.name) != null; i++) p.name = name + " (" + i + ")";
                        projects.add(p);
                        moved++;
                    }
                }
                Files.move(old, old.resolveSibling(old.getFileName() + ".migrated"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (java.nio.file.NoSuchFileException ignored) {
            // No config folder yet: nothing to migrate.
        } catch (Exception e) {
            PlannerClient.LOG.warn("could not migrate old per-world project files", e);
        }
        if (moved > 0) {
            PlannerClient.LOG.info("moved {} projects from per-world files into {}", moved, FILE);
            save();
        }
    }

    public static void save() {
        if (!writable || projects == null) return;
        try {
            Files.createDirectories(dir());
            Path tmp = dir().resolve(FILE + ".tmp");
            Files.writeString(tmp, GSON.toJson(projects), StandardCharsets.UTF_8);
            Files.move(tmp, dir().resolve(FILE), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            PlannerClient.LOG.warn("could not save projects", e);
        }
    }
}
