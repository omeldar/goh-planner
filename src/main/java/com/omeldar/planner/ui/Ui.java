package com.omeldar.planner.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Small shared pieces for the Planner screens. */
final class Ui {
    static final int TITLE = 0xFFFFAA00;
    static final int TEXT = 0xFFDDDDDD;
    static final int DIM = 0xFF999999;
    static final int ROW_HOVER = 0x30FFFFFF;
    static final int ROW_SELECTED = 0x50FFAA00;
    static final int BAR_BACK = 0xFF333333;
    static final int BAR_DONE = 0xFF55CC55;
    static final int BAR_PART = 0xFFFFAA00;

    private Ui() {}

    /** A text field that only takes a number: digits, one decimal point, thousands commas. */
    static TextFieldWidget numberField(TextRenderer font, int x, int y, int w, String initial) {
        TextFieldWidget f = new TextFieldWidget(font, x, y, w, 20, Text.literal("amount"));
        f.setMaxLength(14);
        f.setTextPredicate(s -> s.matches("[0-9,]*\\.?[0-9]*"));
        f.setText(initial);
        return f;
    }

    /** The number in a field, or -1 when it is empty or not a number. */
    static double number(TextFieldWidget f) {
        try {
            String s = f.getText().replace(",", "").trim();
            return s.isEmpty() ? -1 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static void bar(DrawContext ctx, int x, int y, int w, double fraction) {
        ctx.fill(x, y, x + w, y + 2, BAR_BACK);
        int filled = (int) Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) ctx.fill(x, y, x + filled, y + 2, fraction >= 1 ? BAR_DONE : BAR_PART);
    }

    /** A small square button drawn by hand, for the per-row - and +. */
    static void box(DrawContext ctx, TextRenderer font, int x, int y, int size, String label, boolean hover) {
        ctx.fill(x, y, x + size, y + size, hover ? 0xFF777777 : 0xFF444444);
        ctx.fill(x, y, x + size, y + 1, 0xFF999999);
        ctx.drawCenteredTextWithShadow(font, Text.literal(label), x + size / 2 + 1, y + 2, 0xFFFFFFFF);
    }

    static String trim(TextRenderer font, String s, int width) {
        return font.getWidth(s) <= width ? s : font.trimToWidth(s, Math.max(0, width - font.getWidth("..."))) + "...";
    }

    static String plain(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
