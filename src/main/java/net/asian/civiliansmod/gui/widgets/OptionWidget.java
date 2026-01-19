package net.asian.civiliansmod.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class OptionWidget extends ClickableWidget {

    public enum LayoutMode {
        CHECKBOX_LEFT_SCROLL_TEXT,
        CHECKBOX_RIGHT_CLIP_TEXT
    }

    private static final Identifier BUTTON = Identifier.ofVanilla("widget/button");

    private final CheckboxWidget checkbox;
    private final LayoutMode mode;

    private static final int PADDING = 4;
    private static final int CHECKBOX_SIZE = 9;

    private boolean drawBackground = true;
    private boolean showCheckbox = true;

    public OptionWidget(int x, int y, int width, int height, Text message, boolean initialValue, LayoutMode mode, java.util.function.Consumer<Boolean> onChange) {
        super(x, y, width, height, message);
        this.mode = mode;

        this.checkbox = new CheckboxWidget(0, 0, CHECKBOX_SIZE, CHECKBOX_SIZE, Text.empty(), initialValue, onChange);
    }

    public void setDrawBackground(boolean value) { this.drawBackground = value; }

    public void setShowCheckbox(boolean show) {
        this.showCheckbox = show;
    }

    public boolean isChecked() {
        return checkbox.isChecked();
    }

    public void setChecked(boolean checked) {
        checkbox.setChecked(checked);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE, getMessage());
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {

        if (drawBackground) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, BUTTON, getX(), getY(), getWidth(), getHeight());
        }

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;

        if (mode == LayoutMode.CHECKBOX_LEFT_SCROLL_TEXT) {
            renderCheckboxLeftScrollText(context, renderer);
        } else {
            renderTextLeftClipCheckbox(context, renderer);
        }

        if (showCheckbox) {
            checkbox.renderWidget(context, mouseX, mouseY, delta);
        }
    }

    private void renderCheckboxLeftScrollText(DrawContext context, TextRenderer renderer) {

        int checkboxX = getX() + PADDING;
        int size = Math.min(CHECKBOX_SIZE, getHeight() - 4);
        int checkboxY = getY() + (getHeight() - size) / 2 -2;

        checkbox.setX(checkboxX);
        checkbox.setY(checkboxY);

        int textX = checkboxX + CHECKBOX_SIZE + PADDING - 1;
        int textW = getX() + getWidth() - PADDING - textX;

        int textY = getY() + (getHeight() - renderer.fontHeight) / 2;

        int textWidth = renderer.getWidth(getMessage());
        int drawX = textX;

        if (textWidth > textW) {
            int overflow = textWidth - textW;
            int scroll = (int) ((System.currentTimeMillis() / 100) % (overflow + 20));
            drawX = textX - scroll;
        }

        context.enableScissor(textX, getY() + 1, textX + textW, getY() + getHeight() - 1);

        context.drawTextWithShadow(renderer, getMessage(), drawX, textY, 0xFFFFFFFF);

        context.disableScissor();
    }

    private void renderTextLeftClipCheckbox(DrawContext context, TextRenderer renderer) {

        int checkboxX = getX() + getWidth() - CHECKBOX_SIZE - PADDING;
        int checkboxY = getY() + (getHeight() - CHECKBOX_SIZE) / 2;

        checkbox.setX(checkboxX);
        checkbox.setY(checkboxY);

        int textX = getX() + PADDING;
        int textW = checkboxX - textX - PADDING;
        int textY = getY() + (getHeight() - renderer.fontHeight) / 2;

        String message = getMessage().getString();
        String trimmed = message;

        if (renderer.getWidth(message) > textW) {
            trimmed = renderer.trimToWidth(message, textW);
            while (renderer.getWidth(trimmed + "…") > textW && !trimmed.isEmpty()) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            trimmed += "…";
        }

        context.drawText(renderer, trimmed, textX, textY, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (checkbox.isMouseOver(mouseX, mouseY)) {
            checkbox.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
