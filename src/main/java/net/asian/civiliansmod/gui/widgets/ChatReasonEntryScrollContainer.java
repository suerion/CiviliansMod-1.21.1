package net.asian.civiliansmod.gui.widgets;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.gui.CustomChatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class ChatReasonEntryScrollContainer extends ElementListWidget.Entry<ChatReasonEntryScrollContainer> {
    List<DialogueRowEntry> entries = new ArrayList<>();
    boolean customMode;
    boolean open = true;
    NpcChat.ChatReason chatReason;
    CustomChatScreen screen;
    GlobalChatScrollWidget parent;

    OpenWidget openWidget;

    public ChatReasonEntryScrollContainer(GlobalChatScrollWidget parent, NPCEntity npc, final NpcChat.ChatReason chatReason, List<String> strings, CustomChatScreen screen, boolean customMode) {
        this.parent = parent;
        this.chatReason = chatReason;
        this.customMode = customMode;
        for (int i = 0; i < strings.size(); i += 2) {
            entries.add(new DialogueRowEntry(parent, npc, chatReason, strings.subList(i, Math.min(i + 2, strings.size())), i, screen, customMode));
        }
        if (strings.size() % 2 == 0) {
            entries.add(new DialogueRowEntry(parent, npc, chatReason, new ArrayList<>(), strings.size(), screen, customMode));
        }
        if (customMode && strings.isEmpty()) {
            entries.add(new DialogueRowEntry(parent, npc, chatReason, new ArrayList<>(), 0, screen, true));
        }

        openWidget = new OpenWidget(0, 0, 10, 10, this, button -> open = !open);
    }

    @Override
    public List<? extends Selectable> selectableChildren() {
        return List.of();
    }

    @Override
    public List<? extends Element> children() {
        return List.of();
    }

    @Override
    public void render(DrawContext context, int index, int y, boolean hovered, float tickDelta) {

        MinecraftClient client = MinecraftClient.getInstance();

        int x = parent.getRowLeft();
        int entryWidth = 145;

        //background first
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("widget/button"), x, y, entryWidth, 13, ColorHelper.fromFloats(1.0f, 0.5f, 0.5f, 1.0f));

        //arrow
        openWidget.setX(x + 1);
        openWidget.setY(y);
        openWidget.render(context, 0, 0, tickDelta);

        //dialogue up also with Pipelines
        context.drawTextWithShadow(client.textRenderer, String.valueOf(chatReason.getName()), x + 15, y + 3, 0xFFFFFFFF);

        //entrys
        if (open) {
            int offsetY = 15;
            for (DialogueRowEntry entry : entries) {
                entry.render(context, index, y + offsetY, hovered, tickDelta);
                offsetY += 15;
            }
        }
    }

    public int getHeight() {
        return 15 + (open ? (entries.size() * 15) : 0);
    }

    public boolean handleClick(Click click, boolean isDoubleClick) {
        if (openWidget.isMouseOver(click.x(), click.y())) {
            openWidget.mouseClicked(click, isDoubleClick);
            return true;
        }

        if (!open || entries.isEmpty()) return false;

        for (DialogueRowEntry row : entries) {
            if (row.mouseClicked(click, isDoubleClick)) return true;
            for (AbstractDialogueEntry dialogue : row.dialogueEntryList) {
                if (dialogue.mouseClicked(click, isDoubleClick)) return true;
            }
        }
        return false;
    }


    @Override
    public boolean mouseClicked(Click click, boolean isDoubleClick) {
        return handleClick(click, isDoubleClick);
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean getOpen() {
        return open;
    }
}
