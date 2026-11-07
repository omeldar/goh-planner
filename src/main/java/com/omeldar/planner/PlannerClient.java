package com.omeldar.planner;

import com.omeldar.planner.bridge.ApiServer;
import com.omeldar.planner.bridge.LumiLink;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Planner: what a build needs, in the units you store it in. Client-side only.
 *
 * <p>Costs nothing in play. There is no HUD and no rendering outside its own screens, and the
 * one tick listener only opens a screen a command asked for.
 */
public final class PlannerClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("planner");
    private static Screen pending;

    @Override
    public void onInitializeClient() {
        Config.load();
        Commands.register();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (pending != null && mc.currentScreen == null) {
                Screen s = pending;
                pending = null;
                mc.setScreen(s);
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            ApiServer.start();
            LumiLink.announce();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            LumiLink.withdraw();
            ApiServer.stop();
        });
        LOG.info("planner ready");
    }

    /** Open a screen on the next tick, after the chat screen that ran a command has closed. */
    public static void openLater(Screen s) {
        pending = s;
    }

    public static void say(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || message == null || message.isBlank()) return;
        mc.player.sendMessage(Text.literal("[Plan] ").formatted(Formatting.GOLD)
                .append(Text.literal(message).formatted(Formatting.GRAY)), false);
    }
}
