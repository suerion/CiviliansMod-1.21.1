package net.asian.civiliansmod.gui.widgets;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

@SuppressWarnings("unused")
@Environment(EnvType.CLIENT)
public class TextButtonWidget extends ButtonWidget {

    private static final ButtonTextures TEXTURES = new ButtonTextures(
            Identifier.ofVanilla("widget/button"),
            Identifier.ofVanilla("widget/button_disabled"),
            Identifier.ofVanilla("widget/button_highlighted")
    );

    private int textColor = 0xFFFFFF;

    public TextButtonWidget(int x, int y, int width, int height, Text text, PressAction onPress) {
        super(x, y, width, height, text, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    public TextButtonWidget(int x, int y, int width, int height, Text text, PressAction onPress, int textColor) {
        super(x, y, width, height, text, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.textColor = textColor;
    }

    public TextButtonWidget(int x, int y, int width, int height, Text text, PressAction onPress, int textColor, int buttonColor) {
        super(x, y, width, height, text, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.textColor = textColor;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {

        Identifier button = TEXTURES.get(this.active, this.isHovered());
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, button, this.getX(), this.getY(), this.getWidth(), this.getHeight());

        // color
        int baseColor = this.active ? this.textColor : 0xA0A0A0;
        int argbcolor = baseColor | (MathHelper.ceil(this.alpha * 255.0F) << 24);

        // center
        var renderer = MinecraftClient.getInstance().textRenderer;
        int textX = getX() + (width - renderer.getWidth(getMessage())) / 2;
        int textY = getY() + (height - 8) / 2;

        context.drawText(renderer, getMessage(), textX, textY, argbcolor, false);
    }
}