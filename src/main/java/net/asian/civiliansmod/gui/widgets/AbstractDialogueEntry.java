package net.asian.civiliansmod.gui.widgets;

import net.asian.civiliansmod.chat.NpcChat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;

public class AbstractDialogueEntry extends ButtonWidget {
    NpcChat.ChatReason chatReason;

    protected AbstractDialogueEntry(int x, int y, int width, int height,  NpcChat.ChatReason chatReason, PressAction onPress) {
        super(x, y, width, height, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.chatReason = chatReason;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int color = isMouseOver(mouseX, mouseY)
                ? ColorHelper.fromFloats(0.75f, 0.75f, 0.75f, 0.75f)
                : ColorHelper.fromFloats(1.0f, 1.0f, 1.0f, 1.0f);

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("widget/button"), getX(), getY(), getWidth(), getHeight(), color);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (this.active && this.visible && this.isMouseOver(click.x(), click.y())) {
            this.onPress.onPress(this);
            return true;
        }
        return false;
    }
}
