package net.asian.civiliansmod.gui.widgets;

import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.gui.AbstractNPCScreen;
import net.asian.civiliansmod.gui.EditDialogueScreen;
import net.asian.civiliansmod.gui.ConfirmScreen;
import net.asian.civiliansmod.networking.payload.npc.dialogue.RemoveDialoguePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.MultilineText;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class DialogueEntryWidget extends ClickableWidget {

    private static final int DELETE_SIZE = 10;
    private static final Identifier DELETE_BT = Identifier.of("civiliansmod", "textures/gui/delete_button.png");
    private static final Identifier DELETE_BT_HOVER = Identifier.of("civiliansmod", "textures/gui/delete_button_hover.png");

    private final NPCEntity npc;
    private final NpcChat.ChatReason category;
    private final String dialogue;
    private final int index;
    private final Runnable refreshScreen;

    private final OptionWidget option;
    private boolean selectionMode = false;

    public DialogueEntryWidget(NPCEntity npc, int x, int y, int width, int height, NpcChat.ChatReason category, String dialogue, int index, Runnable refreshScreen) {
        super(x, y, width, height, Text.empty());
        this.npc = npc;
        this.category = category;
        this.dialogue = dialogue;
        this.index = index;
        this.refreshScreen = refreshScreen;

        option = new OptionWidget(x, y, width, height - 4, Text.literal(dialogue), false, OptionWidget.LayoutMode.CHECKBOX_RIGHT_CLIP_TEXT, checked -> {});
    }

    private void openDeleteConfirm() {
        MinecraftClient client = MinecraftClient.getInstance();
        MultilineText text = MultilineText.create(client.textRenderer, Text.literal("Delete Dialogue?"), 200);

        client.setScreen(new ConfirmScreen(client.currentScreen,
                yes -> {
                    String lang = client.getLanguageManager().getLanguage();

                    npc.getChatManager()
                            .getTranslatedDialogues(lang)
                            .computeIfAbsent(category, c -> new java.util.ArrayList<>())
                            .remove(dialogue);

                    ClientPlayNetworking.send(new RemoveDialoguePayload(npc.getUuid(), lang, category.toString(), dialogue));
                    refreshScreen.run();
                },
                no -> MinecraftClient.getInstance().setScreen(client.currentScreen), text, 0xFFFFFF));
    }

    public void setSelectionMode(boolean state) {
        this.selectionMode = state;
    }

    public boolean isSelected() {
        return option.isChecked();
    }

    public String getDialogue() {
        return dialogue;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {

        int btcolor = 0xFFFFFFFF;
        if (!this.active) btcolor = 0xFF808080;
        else if (isHovered()) btcolor = 0xFFC0C0C0;

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("widget/button"), getX(), getY(), getWidth(), getHeight(), btcolor);

        option.setX(getX());
        option.setY(getY());
        option.setWidth(getWidth());
        option.setHeight(getHeight());
        option.setDrawBackground(false);

        if (selectionMode) {
            option.setShowCheckbox(true);
            option.renderWidget(context, mouseX, mouseY, delta);
        } else {
            option.setShowCheckbox(false);

            var renderer = MinecraftClient.getInstance().textRenderer;

            int rightPadding = DELETE_SIZE + 6;
            int availableWidth = getWidth() - rightPadding - 6;

            String trimmed = dialogue;

            if (renderer.getWidth(dialogue) > availableWidth) {
                trimmed = renderer.trimToWidth(dialogue, availableWidth);
                while (renderer.getWidth(trimmed + "...") > availableWidth && !trimmed.isEmpty()) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                trimmed += "...";
            }

            int textY = getY() + (getHeight() - renderer.fontHeight) / 2;
            context.drawText(renderer, trimmed, getX() + 4, textY, 0xFFFFFFFF, false);
        }

        if (!selectionMode) {
            int deleteX = getX() + getWidth() - DELETE_SIZE - 2;
            int deleteY = getY() + (getHeight() - DELETE_SIZE) / 2;

            boolean hoveredDelete =
                    mouseX >= deleteX && mouseX < deleteX + DELETE_SIZE &&
                            mouseY >= deleteY && mouseY < deleteY + DELETE_SIZE;

            Identifier icon = hoveredDelete ? DELETE_BT_HOVER : DELETE_BT;

            context.drawTexture(RenderPipelines.GUI_TEXTURED, icon, deleteX, deleteY, 0, 0, DELETE_SIZE, DELETE_SIZE, DELETE_SIZE, DELETE_SIZE, DELETE_SIZE, DELETE_SIZE, -1);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (selectionMode) {
            if (option.isMouseOver(mouseX, mouseY)) {
                option.mouseClicked(mouseX, mouseY, button);
                return true;
            }
            return false;
        }

        int deleteX = getX() + getWidth() - DELETE_SIZE - 2;
        int deleteY = getY() + (getHeight() - DELETE_SIZE) / 2;

        if (mouseX >= deleteX && mouseX < deleteX + DELETE_SIZE &&
                mouseY >= deleteY && mouseY < deleteY + DELETE_SIZE) {

            openDeleteConfirm();
            return true;
        }

        if (isMouseOver(mouseX, mouseY)) {
            var current = MinecraftClient.getInstance().currentScreen;

            if (current instanceof AbstractNPCScreen parent) {
                MinecraftClient.getInstance().setScreen(
                        new EditDialogueScreen(npc, dialogue, category, index, parent)
                );
            }
            return true;
        }
        return false;
    }
}
