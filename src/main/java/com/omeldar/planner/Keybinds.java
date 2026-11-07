package com.omeldar.planner;

import com.omeldar.planner.ui.ProjectListScreen;
import com.omeldar.planner.ui.ProjectScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;

/**
 * One key: open the planner. It lands on B, which vanilla leaves free, and goes to the current
 * project if there is one so the common case is a single keypress rather than a list to pick
 * from. Rebindable like any other key, and the GoH setup screen offers it directly.
 */
public final class Keybinds {
    private static KeyBinding open;

    private Keybinds() {}

    public static void register() {
        open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.planner.open", GLFW.GLFW_KEY_B, KeyBinding.Category.MISC));
    }

    public static void onTick(MinecraftClient mc) {
        if (open == null) return;
        while (open.wasPressed()) {
            if (mc.currentScreen != null || mc.player == null) continue;
            if (!Store.ready()) {
                PlannerClient.say("not in a world yet");
                continue;
            }
            Project current = Store.current();
            mc.setScreen(current != null
                    ? new ProjectScreen(new ProjectListScreen(), current)
                    : new ProjectListScreen());
        }
    }
}
