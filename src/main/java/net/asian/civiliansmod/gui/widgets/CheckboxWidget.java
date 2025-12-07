package net.asian.civiliansmod.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

public class CheckboxWidget extends PressableWidget {

    private static final Identifier UNCHECKED = Identifier.of("minecraft", "widget/checkbox");
    private static final Identifier UNCHECKED_HL = Identifier.of("minecraft", "widget/checkbox_highlighted");
    private static final Identifier CHECKED = Identifier.of("minecraft", "widget/checkbox_selected");
    private static final Identifier CHECKED_HL = Identifier.of("minecraft", "widget/checkbox_selected_highlighted");

    private boolean checked;
    private final Consumer<Boolean> action;

    public CheckboxWidget(int x, int y, int width, int height, Text message, boolean initialValue, Consumer<Boolean> action) {
        super(x, y, width, height, message);
        this.checked = initialValue;
        this.action = action;
    }

    @Override
    public void onPress() {
        this.checked = !this.checked;
        if (action != null) {
            action.accept(this.checked);
        }
    }

    public boolean isChecked() {
        return this.checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);

        Identifier sprite;
        if (checked) {
            sprite = this.isHovered() ? CHECKED_HL : CHECKED;
        } else {
            sprite = this.isHovered() ? UNCHECKED_HL : UNCHECKED;
        }

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), 12, 12);

        // Draw the label text
        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        context.drawTextWithShadow(renderer, this.getMessage(), this.getX() + 26, this.getY() + (this.height - 8) / 2, 0xFFFFFF
        );
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, this.getMessage());
    }
}