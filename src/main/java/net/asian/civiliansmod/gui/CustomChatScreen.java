package net.asian.civiliansmod.gui;

import java.util.ArrayList;
import java.util.List;

import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.gui.widgets.GlobalChatScrollWidget;
import net.asian.civiliansmod.gui.widgets.TextButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.asian.civiliansmod.networking.payload.npc.dialogue.MassRemoveDialoguePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.asian.civiliansmod.chat.NpcChat;
import java.util.Collections;

public class CustomChatScreen extends AbstractConfigScreen {
    GlobalChatScrollWidget chatScrollWidget;
    private boolean selectionMode = false;
    private boolean screenInitialized = false; // screen is initialized? true if yes

    public CustomChatScreen(NPCEntity npc) {
        super(npc, Text.of("civilians.gui.chat_title"));
        chatScrollWidget = new GlobalChatScrollWidget(npc, MinecraftClient.getInstance(), 236, 134, 0, 0, 10, this, false);
    }

    @Override
    public void init() {
        super.init();
        int x = width / 2;
        int y = height / 2;

        chatScrollWidget.setX(x - 114);
        chatScrollWidget.setY(y - 60);
        chatScrollWidget.refreshChildren();
        this.addDrawableChild(chatScrollWidget);

        screenInitialized = true; //now it should be initialized

        //Add toggleButton
        int buttonWidth = 80;
        int buttonHeight = 15;
        int buttonX = x - (buttonWidth / 2);
        int buttonY = y + 85;
        TextButtonWidget toggleButton = new TextButtonWidget(buttonX, buttonY, buttonWidth, buttonHeight, Text.literal(chatScrollWidget.isCustomMode() ? "Default" : "Custom"), button -> {
            boolean nextMode = !chatScrollWidget.isCustomMode();
            this.remove(chatScrollWidget);

            chatScrollWidget = new GlobalChatScrollWidget(npc, MinecraftClient.getInstance(), 236, 134, 0, 0, 10, this, nextMode);

            int newX = width / 2 - 114;
            int newY = height / 2 - 60;
            chatScrollWidget.setX(newX);
            chatScrollWidget.setY(newY);
            chatScrollWidget.refreshChildren();
            chatScrollWidget.refreshScroll();

            this.addDrawableChild(chatScrollWidget);
            button.setMessage(Text.literal(nextMode ? "Default" : "Custom"));

            screenInitialized = true;
        });
        addDrawableChild(toggleButton);

         TextButtonWidget selectModeButton = new TextButtonWidget(
                x - 60, y + 85, 100, 15,
                Text.literal("Select Mode"), button -> {
            selectionMode = !selectionMode;
            chatScrollWidget.setSelectionMode(selectionMode);
            button.setMessage(Text.literal(selectionMode ? "Exit Select Mode" : "Select Mode"));
        },
                0xFFFFFF, 0xFFAAAAFF
        );
        addDrawableChild(selectModeButton);

        //add remove
        int deleteSelectedWidth = 100;
        int deleteSelectedHeight = 15;
        int deleteSelectedX = x - 180;
        int deleteSelectedY = y + 85;

        TextButtonWidget deleteSelectedButton = new TextButtonWidget(
                deleteSelectedX, deleteSelectedY, deleteSelectedWidth, deleteSelectedHeight,
                Text.literal("Delete Selected"), button -> {

            String language = MinecraftClient.getInstance().getLanguageManager().getLanguage();
            boolean isCustom = chatScrollWidget.isCustomMode();

            List<String> selectedDialogues = chatScrollWidget.getSelectedDialogues();
            if (selectedDialogues.isEmpty()) return;

            // für jede Reason (wenn du mehrere Bereiche hast)
            for (NpcChat.ChatReason reason : NpcChat.ChatReason.values()) {
                MassRemoveDialoguePayload payload = new MassRemoveDialoguePayload(
                        npc.getId(),
                        language,
                        reason,
                        selectedDialogues
                );
                ClientPlayNetworking.send(payload);
            }

            // lokal entfernen und refresh
            for (String d : selectedDialogues) {
                if (isCustom)
                    npc.getChatManager().getCustomDialogues().values().forEach(list -> list.remove(d));
                else
                    npc.getChatManager().getTranslatedDialogues(language).values().forEach(list -> list.remove(d));
            }

            this.fullInit();
        }, 0xFFFFFF, 0xFF8888FF);

        addDrawableChild(deleteSelectedButton);

        //add removeallbutton
        int removeButtonWidth = 80;
        int removeButtonHeight = 15;
        int removeButtonX = x + 90;
        int removeButtonY = y + 85;

        TextButtonWidget removeAllButton = new TextButtonWidget(
                removeButtonX, removeButtonY, removeButtonWidth, removeButtonHeight,
                Text.literal("Remove All"), button -> {

            String language = MinecraftClient.getInstance().getLanguageManager().getLanguage();
            boolean isCustom = chatScrollWidget.isCustomMode();

            // Hier iterieren wir über alle ChatReasons
            for (NpcChat.ChatReason reason : NpcChat.ChatReason.values()) {
                List<String> dialoguesToRemove;

                if (isCustom) {
                    dialoguesToRemove = new ArrayList<>(npc.getChatManager()
                            .getCustomDialogues()
                            .getOrDefault(reason, Collections.emptyList()));
                    npc.getChatManager().getCustomDialogues().remove(reason);
                } else {
                    dialoguesToRemove = new ArrayList<>(npc.getChatManager()
                            .getTranslatedDialogues(language)
                            .getOrDefault(reason, Collections.emptyList()));
                    npc.getChatManager().getTranslatedDialogues(language).remove(reason);
                }

                // Sende MassRemovePayload, wenn es tatsächlich Einträge gibt
                if (!dialoguesToRemove.isEmpty()) {
                    MassRemoveDialoguePayload payload = new MassRemoveDialoguePayload(
                            npc.getId(),
                            language,
                            reason,
                            dialoguesToRemove
                    );
                    ClientPlayNetworking.send(payload);
                }
            }

            // Refresh GUI
            this.fullInit();
        }, 0xFFFFFF, 0xFFFF4444);

        addDrawableChild(removeAllButton);
    }


    @Override
    public boolean mouseScrolled(double d, double e, double f, double g) {
        chatScrollWidget.mouseScrolled(d, e, f, g);
        return super.mouseScrolled(d, e, f, g);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (chatScrollWidget != null && screenInitialized) {
            chatScrollWidget.renderWidget(context, mouseX, mouseY, delta);
            // Aktiviert/deaktiviert den Button dynamisch
            for (var child : this.children()) {
                if (child instanceof TextButtonWidget btn && btn.getMessage().getString().contains("Delete Selected")) {
                    btn.active = !chatScrollWidget.getSelectedDialogues().isEmpty();
                }
            }
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        int x = width / 2;
        int y = height / 2;
        Identifier guiTexture = Identifier.of("civiliansmod", "textures/gui/chat_gui.png");
        context.drawTexture(RenderPipelines.GUI_TEXTURED, guiTexture, x - 128, y - 83, 0, 0, 256, 166, 256, 166);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (chatScrollWidget != null && chatScrollWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    //after close reset the screen is initialized
    @Override
    public void close() {
        screenInitialized = false;
        super.close();
    }

    public void fullInit() {
        if (chatScrollWidget == null) return;

        // Speichere, welche Sektionen geöffnet waren
        List<Boolean> openList = new ArrayList<>();
        double offsetY = chatScrollWidget.getScrollY();

        chatScrollWidget.children().forEach(container -> openList.add(container.getOpen()));

        // Neu aufbauen
        chatScrollWidget.refreshChildren();
        chatScrollWidget.setScrollY(Math.min(offsetY, chatScrollWidget.getMaxScrollY()));
        chatScrollWidget.refreshScroll();

        // Setze vorherigen Open-Status wieder
        for (int i = 0; i < chatScrollWidget.children().size() && i < openList.size(); i++) {
            chatScrollWidget.children().get(i).setOpen(openList.get(i));
        }
    }
}
