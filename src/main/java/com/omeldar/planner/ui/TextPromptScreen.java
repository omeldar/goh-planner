package com.omeldar.planner.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Function;

/** Ask for one line of text. The callback returns null to accept, or a reason to stay open. */
final class TextPromptScreen extends Screen {
    private final Screen parent;
    private final String initial;
    private final int maxLength;
    private final Function<String, String> onDone;
    private TextFieldWidget field;
    private String error = "";

    TextPromptScreen(Screen parent, String title, String initial, int maxLength, Function<String, String> onDone) {
        super(Text.literal(title));
        this.parent = parent;
        this.initial = initial;
        this.maxLength = maxLength;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        int w = Math.min(300, width - 40);
        int x = (width - w) / 2;
        int y = height / 2 - 20;
        field = new TextFieldWidget(textRenderer, x, y, w, 20, title);
        field.setMaxLength(maxLength);
        field.setText(initial);
        addDrawableChild(field);
        setInitialFocus(field);
        addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b -> accept())
                .dimensions(x, y + 28, w / 2 - 2, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> back())
                .dimensions(x + w / 2 + 2, y + 28, w / 2 - 2, 20).build());
    }

    private void accept() {
        String failed = onDone.apply(field.getText());
        if (failed == null) back();
        else error = failed;
    }

    private void back() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            accept();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        back();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 40, Ui.TITLE);
        if (!error.isBlank()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(error), width / 2, height / 2 + 36, 0xFFFF5555);
        }
    }
}
