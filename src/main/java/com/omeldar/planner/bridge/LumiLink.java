package com.omeldar.planner.bridge;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.omeldar.planner.Config;
import com.omeldar.planner.PlannerClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tells Lumi this mod exists, by Lumi Link v1: a descriptor in {@code config/lumi-link/} naming
 * the loopback endpoint and its token. Lumi offers the tools under the {@code plan_} prefix.
 * Protocol: goh-autopilot docs/lumi-link.md. Nothing is compiled against Lumi.
 */
public final class LumiLink {
    static final String HINT =
            "Planner is the player's build planner (\"my plan\", \"my project\", \"the castle list\"). "
          + "Projects are one list across all their worlds and servers, each labelled with where "
          + "it was made, so a build planned in a creative copy can be gathered on the server. "
          + "Each material is planned in a container: chests (27 stacks), double chests (54), "
          + "shulker chests (a chest of 27 full shulker boxes), shulker double chests (a double "
          + "chest of 54); the item's real stack size is used (16 for ender pearls, 1 for tools). "
          + "Players keep progress by pressing + per filled container - plan_add_filled is that "
          + "button. Read plan_list_projects before naming a project; plan_show_project opens it "
          + "on screen when they want to see it. When a player describes a build you may "
          + "estimate materials and add them, but say they are estimates. "
          + "For the player: the B key opens it by default (goh_get_status shows the real key if "
          + "GoH is installed), or /plan. Commands: /plan new <name>, /plan open <name>, "
          + "/plan add <item> <amount> [unit], /plan filled <item> [n], /plan count (counts the "
          + "inventory and shulker boxes into have), /plan show. In the screen: Add item, Edit "
          + "(container, amounts, farm rate), Count inv., Copy (a fresh copy to plan a variant).";

    private LumiLink() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("lumi-link").resolve("planner.json");
    }

    static String version() {
        return FabricLoader.getInstance().getModContainer("planner")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    }

    public static void announce() {
        if (!ApiServer.running()) return;
        try {
            JsonObject d = new JsonObject();
            d.addProperty("lumiLink", 1);
            d.addProperty("modId", "planner");
            d.addProperty("name", "Planner");
            d.addProperty("version", version());
            d.addProperty("summary", "Build projects: the materials they need, in stacks, shulkers and "
                    + "double chests, what the player has, and farming time.");
            d.addProperty("prefix", "plan");
            d.addProperty("mcpUrl", "http://127.0.0.1:" + ApiServer.port() + "/mcp");
            d.addProperty("healthUrl", "http://127.0.0.1:" + ApiServer.port() + "/health");
            d.addProperty("tokenHeader", ApiServer.HEADER);
            d.addProperty("token", Config.get().bridgeToken);
            d.addProperty("hint", HINT);
            JsonArray tools = new JsonArray();
            for (var el : Mcp.toolList()) tools.add(el.getAsJsonObject().get("name").getAsString());
            d.add("tools", tools);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(d), StandardCharsets.UTF_8);
            PlannerClient.LOG.info("announced to lumi-link at {}", file());
        } catch (Exception e) {
            PlannerClient.LOG.warn("could not write the lumi-link descriptor", e);
        }
    }

    public static void withdraw() {
        try {
            Files.deleteIfExists(file());
        } catch (Exception ignored) {
            // A stale file only fails Lumi's health check.
        }
    }
}
