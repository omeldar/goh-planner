package com.omeldar.planner.ui;

import com.omeldar.planner.Items;
import com.omeldar.planner.Plans;
import com.omeldar.planner.Project;
import com.omeldar.planner.Storage;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * One project: every material with how many you have, how much that is in storage terms and a
 * progress bar; the selected one in full detail - stacks, shulkers, double chests, double
 * chests of shulkers, how to put it away, and how long the farm needs for the rest.
 *
 * <p>Laid out from the bottom: buttons, then the detail lines, and the list gets the rest of
 * the height and scrolls. So it fits the default window at the default GUI scale.
 */
public final class ProjectScreen extends Screen {
    private static final int ROW = 18;
    /** The - and + boxes at the end of each row: one container filled or taken out. */
    private static final int BOX = 12;
    private static final int W = 380;

    private final Screen parent;
    private final Project project;
    private int selected = -1;
    private int scroll;
    private int listTop;
    private int listBottom;
    private int detailTop;
    private String status = "";

    public ProjectScreen(Screen parent, Project project) {
        super(Text.literal(project.name));
        this.parent = parent;
        this.project = project;
    }

    private int w() {
        return Math.min(W, width - 20);
    }

    @Override
    protected void init() {
        int w = w();
        int x = (width - w) / 2;
        int y2 = height - 26;
        int y1 = y2 - 24;
        detailTop = y1 - 36;
        listTop = 34;
        listBottom = detailTop - 4;
        int bw = (w - 12) / 4;
        Project.Entry sel = selectedEntry();

        addDrawableChild(ButtonWidget.builder(Text.literal("Add item"), b -> {
            if (client != null) client.setScreen(new ItemPickerScreen(this, project));
        }).dimensions(x, y1, bw, 20).build());
        ButtonWidget edit = ButtonWidget.builder(Text.literal("Edit"), b -> {
            Project.Entry e = selectedEntry();
            if (e != null && client != null) client.setScreen(new EntryEditScreen(this, project, e));
        }).dimensions(x + bw + 4, y1, bw, 20).build();
        ButtonWidget remove = ButtonWidget.builder(Text.literal("Remove"), b -> {
            Project.Entry e = selectedEntry();
            if (e == null) return;
            status = Plans.remove(project, e.item);
            selected = -1;
            clearAndInit();
        }).dimensions(x + 2 * (bw + 4), y1, bw, 20).build();
        edit.active = remove.active = sel != null;
        addDrawableChild(edit);
        addDrawableChild(remove);
        ButtonWidget count = ButtonWidget.builder(Text.literal("Count inv."), b -> {
            status = Plans.countInventory(project);
            clearAndInit();
        }).dimensions(x + 3 * (bw + 4), y1, bw, 20).build();
        count.setTooltip(Tooltip.of(Text.literal("Set \"have\" for every material to what you carry "
                + "right now, shulker box contents included. Replaces, so it never counts twice.")));
        count.active = !project.entries.isEmpty();
        addDrawableChild(count);

        addDrawableChild(ButtonWidget.builder(Text.literal("Notes"), b -> {
            if (client != null) client.setScreen(new TextPromptScreen(this, "Notes for " + project.name,
                    project.notes, 256, s -> {
                        project.notes = s.trim();
                        com.omeldar.planner.Store.save();
                        return null;
                    }));
        }).dimensions(x, y2, w / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(x + w / 2 + 2, y2, w / 2 - 2, 20).build());
    }

    private Project.Entry selectedEntry() {
        return selected >= 0 && selected < project.entries.size() ? project.entries.get(selected) : null;
    }

    private int rowAt(double mx, double my) {
        int x = (width - w()) / 2;
        if (mx < x || mx > x + w() || my < listTop || my >= listBottom) return -1;
        int i = (int) ((my - listTop + scroll) / ROW);
        return i < project.entries.size() ? i : -1;
    }

    /** -1 for the minus box, +1 for the plus box, 0 for neither, in row i. */
    private int boxAt(int i, double mx, double my) {
        int x = (width - w()) / 2 + w() - 2 * BOX - 6;
        int y = listTop + i * ROW - scroll + 3;
        if (my < y || my >= y + BOX) return 0;
        if (mx >= x && mx < x + BOX) return -1;
        if (mx >= x + BOX + 3 && mx < x + 2 * BOX + 3) return 1;
        return 0;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        int i = rowAt(click.x(), click.y());
        if (i < 0) return false;
        int box = boxAt(i, click.x(), click.y());
        if (box != 0) {
            selected = i;
            status = Plans.addFilled(project, project.entries.get(i).item, box);
            clearAndInit();
            return true;
        }
        if (doubled && i == selected && client != null) {
            client.setScreen(new EntryEditScreen(this, project, project.entries.get(i)));
        } else {
            selected = i;
            status = "";
            clearAndInit();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, project.entries.size() * ROW - (listBottom - listTop));
        scroll = (int) Math.max(0, Math.min(max, scroll - vertical * ROW));
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int w = w();
        int x = (width - w) / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, Ui.TITLE);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(totals()), width / 2, 20, Ui.DIM);

        if (project.entries.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("No materials yet. Press Add item."),
                    width / 2, listTop + 10, Ui.DIM);
        }
        int hover = rowAt(mouseX, mouseY);
        ctx.enableScissor(0, listTop, width, listBottom);
        for (int i = 0; i < project.entries.size(); i++) {
            int y = listTop + i * ROW - scroll;
            if (y + ROW < listTop || y > listBottom) continue;
            Project.Entry e = project.entries.get(i);
            int size = Items.stackSize(e.item);
            if (i == selected) ctx.fill(x, y, x + w, y + ROW, Ui.ROW_SELECTED);
            else if (i == hover) ctx.fill(x, y, x + w, y + ROW, Ui.ROW_HOVER);
            ctx.drawItem(Items.stack(e.item), x + 1, y + 1);
            String right = e.inUnits();
            int rw = textRenderer.getWidth(right);
            int boxes = 2 * BOX + 9;
            ctx.drawTextWithShadow(textRenderer, Ui.trim(textRenderer, Items.name(e.item), w - rw - boxes - 30),
                    x + 20, y + 3, Ui.TEXT);
            ctx.drawTextWithShadow(textRenderer, right, x + w - rw - boxes - 3, y + 3,
                    e.missing() == 0 && e.target > 0 ? Ui.BAR_DONE : Ui.DIM);
            Ui.bar(ctx, x + 20, y + 14, w - boxes - 24, e.target == 0 ? 1 : (double) e.have / e.target);
            int bx = x + w - 2 * BOX - 6;
            int hoverBox = i == hover ? boxAt(i, mouseX, mouseY) : 0;
            Ui.box(ctx, textRenderer, bx, y + 3, BOX, "-", hoverBox == -1);
            Ui.box(ctx, textRenderer, bx + BOX + 3, y + 3, BOX, "+", hoverBox == 1);
        }
        ctx.disableScissor();

        int y = detailTop;
        for (OrderedText line : detail(w)) {
            ctx.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, Ui.TEXT);
            y += 10;
            if (y > detailTop + 30) break;
        }
    }

    private List<OrderedText> detail(int w) {
        Project.Entry e = selectedEntry();
        String text;
        if (!status.isBlank()) {
            text = status;
        } else if (e == null) {
            text = project.notes.isBlank() ? "Select a material to see how to store and farm it."
                    : project.notes;
        } else {
            int size = Items.stackSize(e.item);
            Storage.Unit u = e.unit();
            text = Items.name(e.item) + ": " + e.inUnits() + " - one " + u.one + " holds "
                 + Storage.fmt(u.perUnit(size)) + ". Press + each time you fill one. Needed in all: "
                 + Storage.summary(e.target, size) + ".";
            if (e.missing() == 0) text += " All gathered.";
            else if (e.ratePerHour > 0) text += " Missing " + Storage.fmt(e.missing()) + ", "
                    + Storage.duration(e.missing() / e.ratePerHour) + " at " + Storage.fmt(Math.round(e.ratePerHour)) + "/h.";
            else text += " Missing " + Storage.fmt(e.missing()) + " (" + Storage.brief(e.missing(), size)
                    + "); set a farm rate with Edit to see how long that takes.";
        }
        return textRenderer.wrapLines(Text.literal(text), w);
    }

    private String totals() {
        long stacks = 0;
        long boxes = 0;
        long missingStacks = 0;
        for (Project.Entry e : project.entries) {
            int size = Items.stackSize(e.item);
            stacks += Storage.stacks(e.target, size);
            boxes += Storage.shulkers(e.target, size);
            missingStacks += Storage.stacks(e.missing(), size);
        }
        if (project.entries.isEmpty()) return "";
        return Storage.fmt(stacks) + " stacks = " + Storage.fmt(boxes) + " shulkers = "
             + Storage.fmt((stacks + Storage.DOUBLE_CHEST - 1) / Storage.DOUBLE_CHEST) + " double chests. "
             + Math.round(project.progress() * 100) + "% gathered, " + Storage.fmt(missingStacks) + " stacks to go.";
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
