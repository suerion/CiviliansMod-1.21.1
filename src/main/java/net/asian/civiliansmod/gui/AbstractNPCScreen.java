package net.asian.civiliansmod.gui;

import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.gui.widgets.CheckboxWidget;
import net.asian.civiliansmod.networking.NPCDataPayload;
import net.asian.civiliansmod.networking.payload.npc.dialogue.MassRemoveDialoguePayload;
import net.asian.civiliansmod.networking.payload.npc.skin.ChangeBaseSkinPayload;
import net.asian.civiliansmod.networking.payload.npc.skin.ChangeSkinPayload;
import net.asian.civiliansmod.trades.TradeManager;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.SkinIdentifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractNPCScreen extends Screen {

    protected enum Tab {
        SKINS("Skins"), AI("Behavior"), DIALOGUES("Dialogues"), TRADES("Trades");
        private final Text title;
        Tab(String title) { this.title = Text.literal(title); }
    }

    protected final NPCEntity npc;
    private NPCEntity previewNpc;
    protected Tab currentTab;
    private TextFieldWidget nameInputField;
    private float entityRotation = 150.0f;
    protected int containerX, containerY, containerWidth, containerHeight;
    private List<Integer> skinsToRender;
    private int selectedSkinIndex = -1;
    private boolean battleBuddyState, stayState, followState, dialogueOrderedState;
    private float wanderRadiusState;
    private final Map<String, CheckboxWidget> dialogueCheckboxes = new HashMap<>();
    private ButtonWidget deleteDialogueButton;
    private int dialogueScrollOffset = 0;
    private String tradePresetState;

    public AbstractNPCScreen(NPCEntity npc, Tab startingTab) {
        super(Text.literal("Civilian Customizer"));
        this.npc = npc;
        this.currentTab = startingTab;
        this.battleBuddyState = npc.isBattleBuddy();
        this.stayState = npc.isPaused();
        this.followState = npc.isFollowing();
        this.wanderRadiusState = npc.getWanderRadius();
        this.dialogueOrderedState = npc.isDialogueOrdered();
        this.tradePresetState = npc.getTradePreset();
    }
    
    // Legacy constructor for your other screens
    public AbstractNPCScreen(NPCEntity npc) {
        this(npc, Tab.SKINS);
    }
    
    // Override method for AbstractConfigScreen to identify this as a skin screen
    protected boolean isSkinScreen() {
        return true;
    }
    
    @Override
    public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        super.init();
        if (this.client != null && this.client.world != null && this.previewNpc == null) {
            this.previewNpc = this.createPreviewNpc(npc.getSkinManager().getBaseVariant());
        }
        this.containerWidth = 256;
        this.containerHeight = 200;
        this.containerX = (this.width - this.containerWidth) / 2;
        this.containerY = (this.height - this.containerHeight) / 2;
        String currentName = npc.getCustomName() != null ? npc.getCustomName().getString() : "";
        this.nameInputField = new TextFieldWidget(this.textRenderer, containerX + 8, containerY + 28, 100, 18, Text.empty());
        this.nameInputField.setText(currentName);
        this.nameInputField.setMaxLength(32);
        this.addSelectableChild(this.nameInputField);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), b -> this.saveAndClose()).dimensions(containerX + containerWidth - 88, containerY + containerHeight - 28, 80, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> this.close()).dimensions(containerX + 8, containerY + containerHeight - 28, 80, 20).build());
        int tabY = containerY + 5;
        this.addDrawableChild(ButtonWidget.builder(Tab.SKINS.title, b -> this.switchTab(Tab.SKINS)).dimensions(containerX + 115, tabY, 65, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Tab.AI.title, b -> this.switchTab(Tab.AI)).dimensions(containerX + 185, tabY, 65, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Tab.DIALOGUES.title, b -> this.switchTab(Tab.DIALOGUES)).dimensions(containerX + 115, tabY + 22, 65, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Tab.TRADES.title, b -> this.switchTab(Tab.TRADES)).dimensions(containerX + 185, tabY + 22, 65, 20).build());
        this.initTabWidgets();
    }

    private void switchTab(Tab newTab) {
        if (this.currentTab != newTab) {
            this.currentTab = newTab;
            this.clearChildren();
            this.init();
        }
    }

    private void initTabWidgets() {
        int contentX = containerX + 120;
        int contentY = containerY + 50;
        int contentWidth = 128;
        switch (this.currentTab) {
            case SKINS -> {
                this.skinsToRender = this.getSkinsToRender();
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Wide"), (btn) -> this.client.setScreen(new DefaultNPCScreen(this.npc))).dimensions(containerX + 8, containerY + 50, 49, 20).build());
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Slim"), (btn) -> this.client.setScreen(new SlimNPCScreen(this.npc))).dimensions(containerX + 59, containerY + 50, 49, 20).build());
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Custom"), (btn) -> this.client.setScreen(new CustomNPCScreen(this.npc))).dimensions(containerX + 8, containerY + 72, 100, 20).build());
            }
            case AI -> {
                this.addDrawableChild(new CheckboxWidget(contentX, contentY, 100, 20, Text.literal("Stay"), this.stayState, (checked) -> { this.stayState = checked; if (checked) this.followState = false; this.switchTab(Tab.AI); }));
                CheckboxWidget followCheckbox = new CheckboxWidget(contentX, contentY + 25, 100, 20, Text.literal("Follow"), this.followState, (checked) -> { this.followState = checked; if (checked) this.stayState = false; this.switchTab(Tab.AI); });
                followCheckbox.active = !this.stayState;
                this.addDrawableChild(followCheckbox);
                this.addDrawableChild(new CheckboxWidget(contentX, contentY + 50, 100, 20, Text.literal("Battle Buddy"), this.battleBuddyState, (checked) -> { this.battleBuddyState = checked; this.switchTab(Tab.AI); }));
                SliderWidget wanderSlider = new SliderWidget(contentX - 5, contentY + 80, contentWidth, 20, Text.literal("Wander: " + (int)this.wanderRadiusState), (this.wanderRadiusState - 4.0) / 60.0) {
                    @Override protected void updateMessage() { setMessage(Text.literal("Wander: " + (int)getValue())); }
                    @Override protected void applyValue() { wanderRadiusState = (float)getValue(); }
                    private double getValue() { return 4.0 + this.value * 60.0; }
                };
                wanderSlider.active = !this.stayState && !this.followState;
                this.addDrawableChild(wanderSlider);
            }
            case DIALOGUES -> {
                this.addDrawableChild(new CheckboxWidget(contentX, contentY, 100, 20, Text.literal("Ordered"), this.dialogueOrderedState, (checked) -> this.dialogueOrderedState = checked));
                this.deleteDialogueButton = ButtonWidget.builder(Text.literal("Delete Selected"), (b) -> this.deleteSelectedDialogues()).dimensions(contentX, containerY + containerHeight - 52, 128, 20).build();
                this.addDrawableChild(this.deleteDialogueButton);
                this.dialogueCheckboxes.clear();
                List<String> dialogues = npc.getChatManager().getDialoguesForLanguage("en_us").get(NpcChat.ChatReason.INTERACT);
                if (dialogues != null) {
                    for (String dialogue : dialogues) {
                        String truncated = dialogue.length() > 15 ? dialogue.substring(0, 14) + "..." : dialogue;
                        CheckboxWidget cb = new CheckboxWidget(0, 0, 100, 12, Text.literal(truncated), false, (c) -> this.updateDeleteButton());
                        this.dialogueCheckboxes.put(dialogue, cb);
                    }
                }
                updateDeleteButton();
            }
            case TRADES -> {
                this.addDrawableChild(CyclingButtonWidget.builder(Text::literal).values(TradeManager.getPresetNames()).initially(this.tradePresetState).build(contentX, contentY, contentWidth, 20, Text.literal("Preset"), (b, value) -> this.tradePresetState = value));
            }
        }
    }

    private void deleteSelectedDialogues() {
        List<String> toRemove = new ArrayList<>();
        this.dialogueCheckboxes.forEach((dialogue, checkbox) -> { if (checkbox.isChecked()) { toRemove.add(dialogue); } });
        if (!toRemove.isEmpty()) {
            ClientPlayNetworking.send(new MassRemoveDialoguePayload(npc.getId(), "en_us", NpcChat.ChatReason.INTERACT, toRemove));
            npc.getChatManager().getDialoguesForLanguage("en_us").get(NpcChat.ChatReason.INTERACT).removeAll(toRemove);
            this.switchTab(Tab.DIALOGUES);
        }
    }

    private void updateDeleteButton() {
        if (this.deleteDialogueButton != null) { this.deleteDialogueButton.active = this.dialogueCheckboxes.values().stream().anyMatch(CheckboxWidget::isChecked); }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // FIX: drawGuiTexture now requires RenderPipeline as first parameter
        context.drawGuiTexture(context.getGuiPipelineType(), Identifier.of("minecraft", "recipe_book/recipe_book"), containerX, containerY, containerWidth, containerHeight);
        context.drawText(this.textRenderer, this.title, this.containerX + 8, this.containerY + 8, 0x404040, false);
        context.drawText(this.textRenderer, this.currentTab.title, this.containerX + 120, this.containerY + 8, 0x404040, false);
        this.nameInputField.render(context, mouseX, mouseY, delta);
        this.renderEntityPreview(context, containerX + 60, containerY + 140, 50, mouseX, mouseY);
        switch (this.currentTab) {
            case SKINS -> this.renderSkinsTab(context, mouseX, mouseY);
            case DIALOGUES -> this.renderDialoguesTab(context, mouseX, mouseY);
            default -> {} // Other tabs do not need special rendering
        }
        // FIX: Changed Widget to Drawable to access render method
        for (var element : children()) {
            if (element instanceof Drawable drawable) { 
                drawable.render(context, mouseX, mouseY, delta); 
            }
        }
    }

    private void renderSkinsTab(DrawContext context, int mouseX, int mouseY) {
        int contentX = containerX + 120, contentY = containerY + 50, skinX = contentX, skinY = contentY, skinsPerRow = 3, skinSize = 40;
        for (int i = 0; i < skinsToRender.size(); i++) {
            renderVariantPreview(context, skinX, skinY, skinsToRender.get(i));
            skinX += skinSize;
            if ((i + 1) % skinsPerRow == 0) { skinX = contentX; skinY += skinSize + 5; }
        }
    }

    private void renderDialoguesTab(DrawContext context, int mouseX, int mouseY) {
        int contentX = containerX + 120, contentY = containerY + 75, yPos = contentY - dialogueScrollOffset;
        context.enableScissor(contentX, contentY, contentX + 128, containerY + containerHeight - 55);
        for (CheckboxWidget checkbox : this.dialogueCheckboxes.values()) {
            checkbox.setX(contentX);
            checkbox.setY(yPos);
            checkbox.render(context, mouseX, mouseY, 0);
            yPos += 15;
        }
        context.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.currentTab == Tab.SKINS) {
            int contentX = containerX + 120, contentY = containerY + 50, skinX = contentX, skinY = contentY, skinsPerRow = 3, skinSize = 40;
            for (int skinId : skinsToRender) {
                if (mouseX >= skinX && mouseX < skinX + skinSize && mouseY >= skinY && mouseY < skinY + skinSize) {
                    this.selectedSkinIndex = skinId;
                    this.previewNpc.getSkinManager().setIdSkin(NPCUtil.getNPCTexture(skinId));
                    return true;
                }
                skinX += skinSize;
                if ((skinsToRender.indexOf(skinId) + 1) % skinsPerRow == 0) { skinX = contentX; skinY += skinSize + 5; }
            }
        }
        if (this.currentTab == Tab.DIALOGUES) {
            for (CheckboxWidget checkbox : this.dialogueCheckboxes.values()) { if (checkbox.mouseClicked(mouseX, mouseY, button)) { return true; } }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.currentTab == Tab.DIALOGUES) {
            this.dialogueScrollOffset = (int)MathHelper.clamp(this.dialogueScrollOffset - verticalAmount * 10, 0, Math.max(0, this.dialogueCheckboxes.size() * 15 - 70));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void renderEntityPreview(DrawContext context, int x, int y, int size, int mouseX, int mouseY) {
        entityRotation = (float) MathHelper.wrapDegrees(entityRotation - (mouseX - x - this.entityRotation) * 0.05f);
        EntityRenderDispatcher entityRenderDispatcher = this.client.getEntityRenderDispatcher();
        Quaternionf quaternionf = new Quaternionf().rotateY(entityRotation * ((float)Math.PI / 180F));
        context.drawEntity(this.previewNpc, x, y, size, new Vector3f(), quaternionf, null);
    }

    private void renderVariantPreview(DrawContext context, int x, int y, int skinId) {
        NPCEntity variantNpc = createPreviewNpc(skinId);
        EntityRenderDispatcher entityRenderDispatcher = this.client.getEntityRenderDispatcher();
        Quaternionf quaternionf = new Quaternionf().rotateY(150 * ((float)Math.PI / 180F));
        context.drawEntity(variantNpc, x + 20, y + 35, 15, new Vector3f(), quaternionf, null);
        if (this.selectedSkinIndex == skinId) { context.drawBorder(x, y, 40, 40, 0xFFFFFFFF); }
    }

    private void saveAndClose() {
        ClientPlayNetworking.send(new NPCDataPayload(npc.getUuid(), nameInputField.getText(), stayState, followState, battleBuddyState, wanderRadiusState, dialogueOrderedState, tradePresetState));
        if (selectedSkinIndex != -1 && selectedSkinIndex != npc.getSkinManager().getBaseVariant()) {
            SkinIdentifier skin = NPCUtil.getNPCTexture(selectedSkinIndex);
            if (skin.custom()) {
                ClientPlayNetworking.send(new ChangeSkinPayload(npc.getUuid(), skin.slim(), skin));
            } else {
                ClientPlayNetworking.send(new ChangeBaseSkinPayload(npc.getUuid(), selectedSkinIndex));
            }
        }
        this.close();
    }

    private NPCEntity createPreviewNpc(int skinId) {
        NPCEntity preview = new NPCEntity(this.npc.getType(), this.client.world);
        preview.getSkinManager().setIdSkin(NPCUtil.getNPCTexture(skinId));
        return preview;
    }

    protected abstract List<Integer> getSkinsToRender();
}