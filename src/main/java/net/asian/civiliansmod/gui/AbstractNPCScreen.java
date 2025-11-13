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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
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

                this.toRender = new ArrayList<>(this.skinsToRender);
                if (this.selectedVariantIndex >= toRender.size()) {
                    this.selectedVariantIndex = 0;
                }

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
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.of("minecraft", "recipe_book/recipe_book"), containerX, containerY, containerWidth, containerHeight);
        context.drawText(this.textRenderer, this.title, this.containerX + 8, this.containerY + 8, 0x404040, false);
        context.drawText(this.textRenderer, this.currentTab.title, this.containerX + 120, this.containerY + 8, 0x404040, false);
        this.nameInputField.render(context, mouseX, mouseY, delta);
        this.renderEntityPreview(context, mouseX, mouseY);
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
            renderVariantPreview(context, skinX, skinY, skinsToRender.get(i), mouseX, mouseY);
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

    //fix to use Entity drawing with the EntityRenderDispatcher, because drawEntity is not usable anymore (old mapping)

    //ADD RENDERCORE

    // Fields for Render State (variant and preview)

    private NPCEntity previewCenter;
    private List<Integer> toRender = new ArrayList<>();
    private int selectedVariantIndex = -1;
    private int originalVariant = 0;
    private int startVariantIndex = 0;

    //smoothing for center preview
    private float smoothHeadYaw = 0.0F;
    private float smoothPitch = 0.0F;

    //constant fields for layout
    private static final int ENTITY_SPACING = 42;       // Abstand der kleinen Vorschauen
    private static final int ENTITY_PREVIEW_SIZE = 25;  // Größe der kleinen NPCs
    private static final int COLUMN_WIDTH = 120;

    @SuppressWarnings("unchecked")
    private void renderEntity(DrawContext context, int x, int y, int scale, LivingEntity entity, boolean isPreview) {
        if (!(entity instanceof LivingEntity living)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();

        // Get renderer
        var renderer = (EntityRenderer<LivingEntity, ? extends EntityRenderState>) dispatcher.getRenderer(entity);

        // Open renderCaptured to bind wildcard to S
        renderCaptured(renderer, entity, context, x, y, scale, client, isPreview);
    }

    private <S extends EntityRenderState> void renderCaptured(
            EntityRenderer<LivingEntity, S> renderer,
            LivingEntity entity,
            DrawContext context,
            int x, int y, int scale,
            MinecraftClient client,
            boolean isPreview) {

        S state = renderer.createRenderState();
        renderer.updateRenderState(entity, state, client.getRenderTickCounter().getTickProgress(false));

        if (isPreview && state instanceof net.minecraft.client.render.entity.state.LivingEntityRenderState s) {
            float headYaw = entity.headYaw;
            float bodyYaw = headYaw * 0.1F;
            s.bodyYaw = bodyYaw;
            s.relativeHeadYaw = headYaw - bodyYaw;
            s.pitch = entity.getPitch();
        }

        Vector3f translation = new Vector3f(0f, 0f, 0f);
        Quaternionf rotation = new Quaternionf()
                .rotateZ((float) Math.toRadians(180f))
                .rotateY((float) Math.toRadians(isPreview ? 192.5f : 165f));
        Quaternionf cameraAngle = new Quaternionf().rotationX((float) Math.toRadians(18f));

        int half = (int) (scale * 2.5f);
        context.addEntity(state, scale, translation, rotation, cameraAngle,
                x - scale, y - half, x + scale, y + half);
    }

    // ENTITY CREATION

    @SuppressWarnings("unchecked")
    private NPCEntity createPreviewNPC(int skinId) {
        World world = MinecraftClient.getInstance().world;
        NPCEntity preview = new NPCEntity((EntityType<? extends PathAwareEntity>) npc.getType(), world);
        preview.getSkinManager().setIdSkin(NPCUtil.getNPCTexture(skinId));
        preview.setAiDisabled(true);
        preview.setSilent(true);
        preview.setHeadYaw(0.0F);
        return preview;
    }

    private NPCEntity createCenterPreviewNPC(int skinId) {
        return createPreviewNPC(skinId);
    }

    //TAB RENDERING

    private void renderVariants(DrawContext context, int mouseX, int mouseY) {
        int containerWidth = 256;
        int containerHeight = 166;
        int containerX = (this.width - containerWidth) / 2;
        int containerY = (this.height - containerHeight) / 2;

        int startY = containerY + 61;
        int panelX = containerX + 77;
        int columnWidth = (COLUMN_WIDTH / 3) - 10;
        int columnOffset = 6;

        int visibleCount = Math.min(toRender.size() - startVariantIndex, 6);
        for (int i = startVariantIndex; i < startVariantIndex + visibleCount; i++) {
            int row = (i - startVariantIndex) / 3;
            int col = (i - startVariantIndex) % 3;
            int x = panelX + col * (columnWidth + columnOffset);
            int y = startY + row * ENTITY_SPACING;
            renderVariantPreview(context, x, y, i, mouseX, mouseY);
        }
    }

    private void renderVariantPreview(DrawContext context, int x, int y, int index, int mouseX, int mouseY) {
        if (toRender.isEmpty()) return;
        if (index < 0 || index >= toRender.size()) return;

        NPCEntity preview = createPreviewNPC(toRender.get(index));
        renderEntity(context, x + ENTITY_PREVIEW_SIZE, y + (ENTITY_SPACING / 2), ENTITY_PREVIEW_SIZE, preview, false);

        int boxX = x + 5, boxY = y - 24, width = 39, height = ENTITY_SPACING;
        if (mouseX >= boxX && mouseX <= boxX + width && mouseY >= boxY && mouseY <= boxY + height) {
            int border = 2;
            int color = 0xFFFFFFFF;
            context.fill(boxX, boxY, boxX + width, boxY + border, color);
            context.fill(boxX, boxY + height - border, boxX + width, boxY + height, color);
            context.fill(boxX, boxY, boxX + border, boxY + height, color);
            context.fill(boxX + width - border, boxY, boxX + width, boxY + height, color);
        }
    }

    private void renderEntityPreview(DrawContext context, int mouseX, int mouseY) {
        if (previewCenter == null) previewCenter = createCenterPreviewNPC(originalVariant);
        NPCEntity preview = previewCenter;

        int variant = (selectedVariantIndex == -1) ? originalVariant : selectedVariantIndex;
        preview.getSkinManager().setIdSkin(NPCUtil.getNPCTexture(variant));
        preview.setAiDisabled(true);
        preview.setSilent(true);

        int guiWidth = 256, guiHeight = 166;
        int guiX = (this.width - guiWidth) / 2, guiY = (this.height - guiHeight) / 2;
        int px = guiX + 36, py = guiY + (guiHeight / 2) + 34;

        float dx = (float) (mouseX - px), dy = (float) (mouseY - py);
        float targetYaw = (-(float) Math.atan2(dx, 50.0) * (180F / (float) Math.PI)) / 2.0F;
        float targetPitch = ((float) Math.atan2(dy, 50.0) * (180F / (float) Math.PI)) / 2.0F;

        targetYaw = MathHelper.clamp(targetYaw, -35.0F, 35.0F);
        targetPitch = MathHelper.clamp(targetPitch, -30.0F, 30.0F);

        smoothHeadYaw += (targetYaw - smoothHeadYaw) * 0.15F;
        smoothPitch += (targetPitch - smoothPitch) * 0.15F;

        float bodyYaw = smoothHeadYaw * 0.1F;
        preview.setYaw(bodyYaw);
        preview.bodyYaw = bodyYaw;
        preview.setHeadYaw(smoothHeadYaw);
        preview.setPitch(smoothPitch);

        renderEntity(context, px, py, 35, preview, true);
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
        World world = MinecraftClient.getInstance().world;

        EntityType<? extends PathAwareEntity> type = (EntityType<? extends PathAwareEntity>) npc.getType();

        NPCEntity preview = new NPCEntity(type, world);
        preview.getSkinManager().setIdSkin(NPCUtil.getNPCTexture(skinId));
        preview.setAiDisabled(true);
        preview.setSilent(true);
        preview.setHeadYaw(0.0F);
        return preview;
    }

    protected abstract List<Integer> getSkinsToRender();
}