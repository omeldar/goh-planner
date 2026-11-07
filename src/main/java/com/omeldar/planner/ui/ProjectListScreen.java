package com.omeldar.planner.ui;

import com.omeldar.planner.Project;
import com.omeldar.planner.Store;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Every project, from every world and server, with where each was made. Plan in a creative
 * copy, gather on the server: it is the same list. Click to select, double-click to open.
 */
public final class ProjectListScreen extends Screen {
    private static final int ROW = 14;
    private static final int MAX_W = 380;

    private int selected = -1;
    private int scroll;
    private boolean confirmDelete;
    private int listTop;
    private int listBottom;

    public ProjectListScreen() {
        super(Text.literal("Projects"));
    }

    private int w() {
        return Math.min(MAX_W, width - 20);
    }

    @Override
    protected void init() {
        Store.ready();
        int W = w();
        int x = (width - W) / 2;
        int y2 = height - 26;
        int y1 = y2 - 24;
        listTop = 28;
        listBottom = y1 - 6;
        Project sel = selectedProject();
        int bw = (W - 16) / 5;

        addDrawableChild(ButtonWidget.builder(Text.literal("New"), b -> prompt("New project", "", name -> {
            String failed = Store.create(name);
            if (failed == null) selected = Store.all().size() - 1;
            return failed;
        })).dimensions(x, y1, bw, 20).build());
        ButtonWidget open = ButtonWidget.builder(Text.literal("Open"), b -> open())
                .dimensions(x + bw + 4, y1, bw, 20).build();
        ButtonWidget copy = ButtonWidget.builder(Text.literal("Copy"), b -> {
            Project p = selectedProject();
            if (p != null) prompt("Copy " + p.name + " as", p.name + " (copy)", name -> Store.duplicate(p, name));
        }).dimensions(x + 2 * (bw + 4), y1, bw, 20).build();
        copy.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(
                "A fresh copy of the materials list, with nothing filled yet.")));
        ButtonWidget rename = ButtonWidget.builder(Text.literal("Rename"), b -> {
            Project p = selectedProject();
            if (p != null) prompt("Rename " + p.name, p.name, name -> Store.rename(p, name));
        }).dimensions(x + 3 * (bw + 4), y1, bw, 20).build();
        ButtonWidget delete = ButtonWidget.builder(Text.literal(confirmDelete ? "Sure?" : "Delete"), b -> {
            Project p = selectedProject();
            if (p == null) return;
            if (!confirmDelete) {
                confirmDelete = true;
            } else {
                Store.delete(p);
                selected = -1;
                confirmDelete = false;
            }
            clearAndInit();
        }).dimensions(x + 4 * (bw + 4), y1, bw, 20).build();
        open.active = copy.active = rename.active = delete.active = sel != null;
        addDrawableChild(open);
        addDrawableChild(copy);
        addDrawableChild(rename);
        addDrawableChild(delete);
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(x, y2, W, 20).build());
    }

    private void prompt(String title, String initial, java.util.function.Function<String, String> done) {
        if (client != null) client.setScreen(new TextPromptScreen(this, title, initial, 40, done));
    }

    private Project selectedProject() {
        List<Project> all = Store.all();
        return selected >= 0 && selected < all.size() ? all.get(selected) : null;
    }

    private void open() {
        Project p = selectedProject();
        if (p == null || client == null) return;
        Store.setCurrent(p);
        client.setScreen(new ProjectScreen(this, p));
    }

    private int rowAt(double mouseX, double mouseY) {
        int W = w();
        int x = (width - W) / 2;
        if (mouseX < x || mouseX > x + W || mouseY < listTop || mouseY >= listBottom) return -1;
        int i = (int) ((mouseY - listTop + scroll) / ROW);
        return i < Store.all().size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        int i = rowAt(click.x(), click.y());
        if (i < 0) return false;
        if (doubled && i == selected) {
            open();
        } else {
            selected = i;
            confirmDelete = false;
            clearAndInit();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, Store.all().size() * ROW - (listBottom - listTop));
        scroll = (int) Math.max(0, Math.min(max, scroll - vertical * ROW * 2));
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, Ui.TITLE);
        int W = w();
        int x = (width - W) / 2;
        List<Project> all = Store.all();
        if (all.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("No projects yet. Press New."),
                    width / 2, listTop + 10, Ui.DIM);
            return;
        }
        int hover = rowAt(mouseX, mouseY);
        ctx.enableScissor(0, listTop, width, listBottom);
        for (int i = 0; i < all.size(); i++) {
            int y = listTop + i * ROW - scroll;
            if (y + ROW < listTop || y > listBottom) continue;
            Project p = all.get(i);
            if (i == selected) ctx.fill(x, y, x + W, y + ROW, Ui.ROW_SELECTED);
            else if (i == hover) ctx.fill(x, y, x + W, y + ROW, Ui.ROW_HOVER);
            String right = p.entries.size() + " materials, " + Math.round(p.progress() * 100) + "%";
            int rw = textRenderer.getWidth(right);
            int nameW = Math.min(textRenderer.getWidth(p.name), (W - rw) / 2);
            ctx.drawTextWithShadow(textRenderer, Ui.trim(textRenderer, p.name, nameW), x + 4, y + 3, Ui.TEXT);
            if (p.world != null && !p.world.isBlank()) {
                ctx.drawTextWithShadow(textRenderer, Ui.trim(textRenderer, p.world, W - rw - nameW - 24),
                        x + 12 + nameW, y + 3, 0xFF6F8FAF);
            }
            ctx.drawTextWithShadow(textRenderer, right, x + W - rw - 4, y + 3, Ui.DIM);
        }
        ctx.disableScissor();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
