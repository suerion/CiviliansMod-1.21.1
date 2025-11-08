package net.asian.civiliansmod.gui.widgets;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.gui.AddDialogueScreen;
import net.asian.civiliansmod.gui.CustomChatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

public class AddDialogueEntry extends AbstractDialogueEntry {
    Identifier TEXTURE = Identifier.of(CiviliansMod.MOD_ID, "textures/gui/add_button.png");
    boolean customMode;

    protected AddDialogueEntry(NPCEntity npc, int x, int y, int width, int height, NpcChat.ChatReason chatReason, CustomChatScreen screen, boolean customMode) {
        super(x, y, width, height, chatReason, button -> {
            MinecraftClient.getInstance().setScreen(new AddDialogueScreen(npc, "", chatReason, screen, customMode));
        });
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int size = 8;
        int drawX = getX() + (getWidth() - size) / 2;
        int drawY = getY() + (getHeight() - size) / 2;

        context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE, drawX, drawY, 0, 0, size, size, size, size);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (this.isMouseOver(click.x(), click.y())) {
            this.onPress.onPress(this);
            return true;
        }
        return false;
    }
}
