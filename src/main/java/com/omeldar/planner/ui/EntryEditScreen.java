package com.omeldar.planner.ui;

import com.omeldar.planner.Items;
import com.omeldar.planner.Project;
import com.omeldar.planner.Storage;
import com.omeldar.planner.Store;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Change one material: which container it is planned in, how many of those the project needs,
 * how many are filled, and how fast the farm makes the item. Switching the container converts
 * the numbers already typed, so "2 double chests" becomes "4 chests" rather than "2 chests".
 */
final class EntryEditScreen extends Screen {
    private final Screen parent;
    private final Project project;
    private final Project.Entry entry;
    private final int size;
    private TextFieldWidget target;
    private TextFieldWidget have;
    private TextFieldWidget rate;
    private Storage.Unit unit;
    private String error = "";

    EntryEditScreen(Screen parent, Project project, Project.Entry entry) {
        super(Text.literal(Items.name(entry.item)));
        this.parent = parent;
        this.project = project;
        this.entry = entry;
        this.size = Items.stackSize(entry.item);
        this.unit = entry.unit();
    }

    private static String num(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d);
        return String.format(Locale.ROOT, "%.2f", d).replaceAll("0+$", "");
    }

    @Override
    protected void init() {
        int w = Math.min(300, width - 20);
        int x = (width - w) / 2;
        int labelW = 80;
        int y = Math.max(34, height / 2 - 60);

        addDrawableChild(ButtonWidget.builder(Text.literal("Planned in: " + unit.label), b -> {
            // Carry the typed amounts across into the new container.
            double t = Ui.number(target);
            double h = Ui.number(have);
            long tItems = t < 0 ? entry.target : unit.toItems(t, size);
            long hItems = h < 0 ? entry.have : unit.toItems(h, size);
            unit = unit.next();
            target.setText(num(unit.of(tItems, size)));
            have.setText(num(unit.of(hItems, size)));
            b.setMessage(Text.literal("Planned in: " + unit.label));
        }).dimensions(x, y, w, 20).build());

        String t0 = target == null ? num(unit.of(entry.target, size)) : target.getText();
        String h0 = have == null ? num(unit.of(entry.have, size)) : have.getText();
        String r0 = rate == null ? (entry.ratePerHour > 0 ? Ui.plain(entry.ratePerHour) : "") : rate.getText();
        target = Ui.numberField(textRenderer, x + labelW, y + 26, w - labelW, t0);
        have = Ui.numberField(textRenderer, x + labelW, y + 50, w - labelW, h0);
        rate = Ui.numberField(textRenderer, x + labelW, y + 74, w - labelW, r0);
        addDrawableChild(target);
        addDrawableChild(have);
        addDrawableChild(rate);

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> save())
                .dimensions(x, y + 102, w / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(x + w / 2 + 2, y + 102, w / 2 - 2, 20).build());
        setInitialFocus(target);
    }

    private void save() {
        double t = Ui.number(target);
        double h = Ui.number(have);
        double r = Ui.number(rate);
        if (t < 0) {
            error = "Type how many " + unit.label + " the project needs.";
            return;
        }
        entry.unit = unit.name();
        entry.target = unit.toItems(t, size);
        entry.have = h < 0 ? 0 : unit.toItems(h, size);
        entry.ratePerHour = r < 0 ? 0 : r;
        Store.save();
        close();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int w = Math.min(300, width - 20);
        int x = (width - w) / 2;
        int y = Math.max(34, height / 2 - 60);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, y - 22, Ui.TITLE);
        ctx.drawItem(Items.stack(entry.item), width / 2 - textRenderer.getWidth(title) / 2 - 20, y - 26);
        ctx.drawTextWithShadow(textRenderer, "Needed", x, y + 32, Ui.TEXT);
        ctx.drawTextWithShadow(textRenderer, "Filled", x, y + 56, Ui.TEXT);
        ctx.drawTextWithShadow(textRenderer, "Farm items/h", x, y + 80, Ui.TEXT);

        String preview = error;
        if (preview.isBlank()) {
            double t = Ui.number(target);
            preview = "One " + unit.one + " holds " + Storage.fmt(unit.perUnit(size)) + " " + Items.name(entry.item)
                    + (t > 0 ? "; needed in all: " + Storage.summary(unit.toItems(t, size), size) : "");
        }
        int py = y + 128;
        for (var line : textRenderer.wrapLines(Text.literal(preview), w)) {
            ctx.drawCenteredTextWithShadow(textRenderer, line, width / 2, py, error.isBlank() ? Ui.DIM : 0xFFFF5555);
            py += 10;
        }
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
