package com.omeldar.planner.ui;

import com.omeldar.planner.Items;
import com.omeldar.planner.Plans;
import com.omeldar.planner.Project;
import com.omeldar.planner.Storage;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Find an item and say how many of which container: chests, double chests, shulker chests,
 * shulker double chests (or boxes, stacks, items). The material is then planned and counted in
 * that container. The line above the buttons shows what it comes to before it is added.
 */
final class ItemPickerScreen extends Screen {
    private static final int CELL = 18;

    private final Screen parent;
    private final Project project;
    private TextFieldWidget search;
    private TextFieldWidget amount;
    private ButtonWidget unitButton;
    private Storage.Unit unit = Storage.Unit.DOUBLE_CHESTS;
    private List<Item> results = new ArrayList<>();
    private Item picked;
    private int scroll;
    private int gridTop;
    private int gridBottom;
    private int cols;
    private String status = "";

    ItemPickerScreen(Screen parent, Project project) {
        super(Text.literal("Add to " + project.name));
        this.parent = parent;
        this.project = project;
    }

    private int gridWidth() {
        return Math.min(width - 20, 18 * 20);
    }

    @Override
    protected void init() {
        int gw = gridWidth();
        int x = (width - gw) / 2;
        cols = gw / CELL;
        String keepSearch = search == null ? "" : search.getText();
        String keepAmount = amount == null ? "1" : amount.getText();

        search = new TextFieldWidget(textRenderer, x, 22, gw, 18, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search items..."));
        search.setText(keepSearch);
        search.setChangedListener(s -> {
            scroll = 0;
            results = Items.search(s, 2000);
        });
        addDrawableChild(search);
        setInitialFocus(search);
        results = Items.search(keepSearch, 2000);

        int y = height - 26;
        gridTop = 44;
        gridBottom = y - 18;
        amount = Ui.numberField(textRenderer, x, y, 60, keepAmount);
        addDrawableChild(amount);
        unitButton = ButtonWidget.builder(Text.literal(unit.label), b -> {
            unit = unit.next();
            b.setMessage(Text.literal(unit.label));
        }).dimensions(x + 64, y, gw - 64 - 128, 20).build();
        addDrawableChild(unitButton);
        ButtonWidget add = ButtonWidget.builder(Text.literal("Add"), b -> add())
                .dimensions(x + gw - 124, y, 60, 20).build();
        addDrawableChild(add);
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(x + gw - 60, y, 60, 20).build());
    }

    private void add() {
        if (picked == null) {
            status = "Click an item first.";
            return;
        }
        double n = Ui.number(amount);
        if (n <= 0) {
            status = "Type how many.";
            return;
        }
        status = Plans.add(project, Items.id(picked), n, unit) + ". Add another, or Done.";
    }

    private int cellAt(double mx, double my) {
        int x = (width - gridWidth()) / 2;
        if (mx < x || mx >= x + cols * CELL || my < gridTop || my >= gridBottom) return -1;
        int c = (int) ((mx - x) / CELL);
        int r = (int) ((my - gridTop + scroll) / CELL);
        int i = r * cols + c;
        return i < results.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        int i = cellAt(click.x(), click.y());
        if (i < 0) return false;
        picked = results.get(i);
        status = "";
        if (doubled) add();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int rows = (results.size() + cols - 1) / Math.max(1, cols);
        int max = Math.max(0, rows * CELL - (gridBottom - gridTop));
        scroll = (int) Math.max(0, Math.min(max, scroll - vertical * CELL * 2));
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, Ui.TITLE);
        int x = (width - gridWidth()) / 2;
        int hover = cellAt(mouseX, mouseY);
        ctx.enableScissor(0, gridTop, width, gridBottom);
        for (int i = 0; i < results.size(); i++) {
            int cx = x + (i % cols) * CELL;
            int cy = gridTop + (i / cols) * CELL - scroll;
            if (cy + CELL < gridTop || cy > gridBottom) continue;
            Item item = results.get(i);
            if (item == picked) ctx.fill(cx, cy, cx + CELL, cy + CELL, Ui.ROW_SELECTED);
            else if (i == hover) ctx.fill(cx, cy, cx + CELL, cy + CELL, Ui.ROW_HOVER);
            ctx.drawItem(new ItemStack(item), cx + 1, cy + 1);
        }
        ctx.disableScissor();
        if (results.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Nothing matches."), width / 2, gridTop + 8, Ui.DIM);
        }

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(Ui.trim(textRenderer, line(), width - 20)),
                width / 2, gridBottom + 4, status.isBlank() ? Ui.TEXT : 0xFFFFFF55);
        if (hover >= 0) {
            ctx.drawTooltip(textRenderer, new ItemStack(results.get(hover)).getName(), mouseX, mouseY);
        }
    }

    /** What the typed amount comes to, for the picked item. */
    private String line() {
        if (!status.isBlank()) return status;
        if (picked == null) return "Click an item, then type an amount and pick the unit.";
        String id = Items.id(picked);
        double n = Ui.number(amount);
        if (n <= 0) return Items.name(id) + " - type how many";
        long items = unit.toItems(n, Items.stackSize(id));
        return Items.name(id) + ": " + Storage.summary(items, Items.stackSize(id));
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
