package net.asian.civiliansmod.gui.widgets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

@SuppressWarnings("unused")
@Environment(EnvType.CLIENT)
public class TextButtonWidget extends ButtonWidget {

    private static final int BG_NORMAL  = 0xFF2B2B2B;
    private static final int BG_HOVER   = 0xFF3A3A3A;
    private static final int BG_BORDER  = 0xFF555555;

    private int buttonColor = BG_NORMAL;
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

        // background
        int background = this.isHovered() ? BG_HOVER : BG_NORMAL;
        context.fill(getX(), getY(), getX() + width, getY() + height, background);

        // border
        context.fill(getX(), getY() + height - 1, getX() + width, getY() + height, BG_BORDER);

        // color
        int color = this.active ? this.textColor : 0xFF777777;

        // center
        var renderer = MinecraftClient.getInstance().textRenderer;
        int textX = getX() + (width - renderer.getWidth(getMessage())) / 2;
        int textY = getY() + (height - 8) / 2;

        context.drawText(renderer, getMessage(), textX, textY, color, false);
    }
}