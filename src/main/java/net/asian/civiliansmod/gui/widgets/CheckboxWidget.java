package net.asian.civiliansmod.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

public class CheckboxWidget extends PressableWidget {

    private static final Identifier TEXTURE = Identifier.of("minecraft", "widget/checkbox");
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
    
    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        // The new identifier points to a texture with different states (e.g., hovered, selected)
        Identifier texture = TEXTURE.withSuffixedPath(this.isChecked() ? "_selected" : "");
        if (this.isHovered()) {
            texture = texture.withSuffixedPath("_highlighted");
        }
        
        // Draw the checkbox texture
        context.drawGuiTexture(null, texture, this.getX(), this.getY(), 20, 20);

        // Draw the label text
        context.drawTextWithShadow(textRenderer, this.getMessage(), this.getX() + 24, this.getY() + (this.height - 8) / 2, 0xFFFFFF);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'appendClickableNarrations'");
    }
}