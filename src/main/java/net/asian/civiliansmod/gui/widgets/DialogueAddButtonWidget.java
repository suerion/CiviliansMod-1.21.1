package net.asian.civiliansmod.gui.widgets;

import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.gui.AbstractNPCScreen;
import net.asian.civiliansmod.gui.AddDialogueScreen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class DialogueAddButtonWidget extends ClickableWidget {

    private final NPCEntity npc;
    private final NpcChat.ChatReason category;
    private final Runnable refreshScreen;

    private static final Identifier ADD_BUTTON = Identifier.of("civiliansmod", "textures/gui/add_button.png");

    private static final int ICON_SIZE = 10;

    public DialogueAddButtonWidget(NPCEntity npc, int x, int y, int width, int height, NpcChat.ChatReason category, Runnable refreshScreen) {
        super(x, y, width, height, Text.empty());

        this.npc = npc;
        this.category = category;
        this.refreshScreen = refreshScreen;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {

        int btcolor = 0xFFFFFFFF;
        if (!this.active) btcolor = 0xFF808080;
        else if (isHovered()) btcolor = 0xFFC0C0C0;

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("widget/button"), getX(), getY(), getWidth(), getHeight(), btcolor);

        int iconX = getX() + (getWidth() / 2) - (ICON_SIZE / 2);
        int iconY = getY() + (getHeight() / 2) - (ICON_SIZE / 2);

        context.drawTexture(RenderPipelines.GUI_TEXTURED,ADD_BUTTON, iconX, iconY, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE,-1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (!isMouseOver(mouseX, mouseY))
            return false;

        MinecraftClient client = MinecraftClient.getInstance();
        var current = client.currentScreen;

        if (current instanceof AbstractNPCScreen parent) {
            client.setScreen(new AddDialogueScreen(npc, "", category, parent));
        }
        return true;
    }
}
