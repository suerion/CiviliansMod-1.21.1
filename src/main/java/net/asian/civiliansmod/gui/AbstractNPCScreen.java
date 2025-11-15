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

import java.util.*;

public abstract class AbstractNPCScreen extends Screen {

    protected enum Tab {
        SKINS("Skins"), AI("Behavior"), DIALOGUES("Dialogues"), TRADES("Trades");
        private final Text title;
        Tab(String title) { this.title = Text.literal(title); }
    }

    //Core NPC DATA
    protected final NPCEntity npc;
    protected Tab currentTab;

    //NPC cache
    private final Map<Integer, NPCEntity> previewNpcCache = new HashMap<>();

    //GUI Layout
    protected int containerX, containerY, containerWidth, containerHeight;
    private TextFieldWidget nameInputField;

    //Skin handling
    private List<Integer> skinsToRender = Collections.emptyList();
    private int selectedSkinIndex = -1;
    private int originalVariantIndex = 0;

    //AI state
    private boolean battleBuddyState, stayState, followState, dialogueOrderedState;
    private float wanderRadiusState;

    //Dialogues state
    private final Map<String, CheckboxWidget> dialogueCheckboxes = new LinkedHashMap<>();
    private ButtonWidget deleteDialogueButton;
    private int dialogueScrollOffset = 0;

    //Trade State
    private String tradePresetState;

    //Render Core
    private NPCEntity previewNpc;

    //smooth head rotation
    private float smoothHeadYaw = 0.0F;
    private float smoothPitch = 0.0F;

    // constants for small preview layout
    private static final int SKIN_CELL_SIZE = 40;
    private static final int SKIN_CELL_SPACING = 5;
    private static final int SKIN_COLUMNS = 3;

    private static final int ENTITY_PREVIEW_SIZE = 25; // small NPCs
    private static final int CENTER_PREVIEW_SIZE = 35; // render center preview

    private boolean pendingTabSwitch = false;
    private Tab tabToSwitchTo = null;

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
    public boolean shouldPause() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        //background box
        this.containerWidth = 286;
        this.containerHeight = 191;
        this.containerX = (this.width - this.containerWidth) / 2;
        this.containerY = (this.height - this.containerHeight) / 2;

        // center preview NPC
        if (this.client != null && this.client.world != null && this.previewNpc == null) {
            this.previewNpc = this.createPreviewNPC(npc.getSkinManager().getBaseVariant());
        }

        // name field
        String currentName = npc.getCustomName() != null ? npc.getCustomName().getString() : "";
        this.nameInputField = new TextFieldWidget(this.textRenderer, containerX + 8, containerY + 28, 100, 18, Text.empty());
        this.nameInputField.setText(currentName);
        this.nameInputField.setMaxLength(32);
        this.addSelectableChild(this.nameInputField);

        //buttons bottom
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), b -> this.saveAndClose()).dimensions(containerX + containerWidth - 88, containerY + containerHeight - 28, 80, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> this.close()).dimensions(containerX + 8, containerY + containerHeight - 28, 80, 20).build());

        //tab buttons
        int tabY = containerY + 5;
        this.addDrawableChild(ButtonWidget.builder(Tab.SKINS.title, b -> this.switchTab(Tab.SKINS)).dimensions(containerX + 115, tabY, 65, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Tab.AI.title, b -> this.switchTab(Tab.AI)).dimensions(containerX + 185, tabY, 65, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Tab.DIALOGUES.title, b -> this.switchTab(Tab.DIALOGUES)).dimensions(containerX + 115, tabY + 22, 65, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Tab.TRADES.title, b -> this.switchTab(Tab.TRADES)).dimensions(containerX + 185, tabY + 22, 65, 20).build());

        //tab widgets
        this.initTabWidgets();
    }

    private void switchTab(Tab newTab) {
        if (this.currentTab != newTab) {
            this.tabToSwitchTo = newTab;
            this.pendingTabSwitch = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingTabSwitch) {
            this.currentTab = tabToSwitchTo;
            this.clearAndInit();
            pendingTabSwitch = false;
            tabToSwitchTo = null;
        }
    }

    private void initTabWidgets() {
        int contentX = containerX + 120;
        int contentY = containerY + 50;
        int contentWidth = 128;
        switch (this.currentTab) {
            case SKINS -> {
                //skins from cubclass
                List<Integer> list = this.getSkinsToRender();
                this.skinsToRender = (list != null) ? list : Collections.emptyList();

                //skin type switch buttons
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Wide"), (btn) -> this.client.setScreen(new DefaultNPCScreen(this.npc))).dimensions(containerX + 8, containerY + 50, 49, 20).build());
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Slim"), (btn) -> this.client.setScreen(new SlimNPCScreen(this.npc))).dimensions(containerX + 59, containerY + 50, 49, 20).build());
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Custom"), (btn) -> this.client.setScreen(new CustomNPCScreen(this.npc))).dimensions(containerX + 8, containerY + 72, 100, 20).build());
            }
            case AI -> {

                final CheckboxWidget[] stayCheckbox = new CheckboxWidget[1];
                final CheckboxWidget[] followCheckbox = new CheckboxWidget[1];

                followCheckbox[0] = new CheckboxWidget(contentX, contentY + 25, 100, 20, Text.literal("Follow"), this.followState, (checked) -> {
                    if (this.followState != checked) {
                        this.followState = checked;
                    }
                    if (checked) {
                        this.stayState = false;
                        stayCheckbox[0].setChecked(false);
                    }
                    stayCheckbox[0].active = !checked;
                    this.clearAndInit();
                    }
                );

                stayCheckbox[0] = new CheckboxWidget(contentX, contentY, 100, 20, Text.literal("Stay"), this.stayState, (checked) -> {
                    if (this.stayState != checked) {
                        this.stayState = checked;
                    }
                    if (checked) {
                        this.followState = false;
                        followCheckbox[0].setChecked(false);
                    }
                    followCheckbox[0].active = !checked;
                    this.clearAndInit();
                    }
                );

                followCheckbox[0].active = !this.stayState;
                stayCheckbox[0].active = !this.followState;

                this.addDrawableChild(stayCheckbox[0]);
                this.addDrawableChild(followCheckbox[0]);

                this.addDrawableChild(new CheckboxWidget(contentX, contentY + 50, 100, 20, Text.literal("Battle Buddy"), this.battleBuddyState, (checked) -> {
                    this.battleBuddyState = checked;

                    if (checked) {
                        this.stayState = false;
                        this.followState = false;

                        stayCheckbox[0].setChecked(false);
                        followCheckbox[0].setChecked(false);

                        stayCheckbox[0].active = false;
                        followCheckbox[0].active = false;
                    } else {
                        stayCheckbox[0].active = true;
                        followCheckbox[0].active = true;
                    }

                    this.clearAndInit();
                }));

                if (!this.stayState && !this.followState && !this.battleBuddyState) {
                    SliderWidget wanderSlider =
                            new SliderWidget(
                                    contentX - 5, contentY + 80,
                                    contentWidth, 20,
                                    Text.literal("Wander: " + (int) this.wanderRadiusState),
                                    (this.wanderRadiusState - 4.0) / 60.0
                            ) {
                                @Override
                                protected void updateMessage() {
                                    setMessage(Text.literal("Wander: " + (int) getValue()));
                                }

                                @Override
                                protected void applyValue() {
                                    wanderRadiusState = (float) getValue();
                                }

                                private double getValue() {
                                    return 4.0 + this.value * 60.0;
                                }
                            };
                    this.addDrawableChild(wanderSlider);
                }
            }
            case DIALOGUES -> {
                // set order of dialouges
                this.addDrawableChild(new CheckboxWidget(contentX, contentY, 100, 20, Text.literal("Ordered"), this.dialogueOrderedState, (checked) -> this.dialogueOrderedState = checked));

                //delete selected button
                this.deleteDialogueButton = ButtonWidget.builder(Text.literal("Delete Selected"), (b) -> this.deleteSelectedDialogues()).dimensions(contentX, containerY + containerHeight - 52, 128, 20).build();
                this.addDrawableChild(this.deleteDialogueButton);

                //fill dialogue list with checkboxes
                this.dialogueCheckboxes.clear();
                Map<NpcChat.ChatReason, List<String>> langMap = npc.getChatManager().getDialoguesForLanguage("en_us");
                List<String> dialogues = (langMap != null) ? langMap.get(NpcChat.ChatReason.INTERACT) : null;
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

    //abstract method for subclasses to define which skin indices they want to render
    protected abstract List<Integer> getSkinsToRender();

    //dialogues logic
    private void deleteSelectedDialogues() {
        List<String> toRemove = new ArrayList<>();
        this.dialogueCheckboxes.forEach((dialogue, checkbox) -> { if (checkbox.isChecked()) { toRemove.add(dialogue); } });
        if (!toRemove.isEmpty()) {
            ClientPlayNetworking.send(new MassRemoveDialoguePayload(npc.getId(), "en_us", NpcChat.ChatReason.INTERACT, toRemove));
            Map<NpcChat.ChatReason, List<String>> langMap = npc.getChatManager().getDialoguesForLanguage("en_us");
            if (langMap != null) {
                List<String> list = langMap.get(NpcChat.ChatReason.INTERACT);
                if (list != null) {
                    list.removeAll(toRemove);
                }
            }
            this.switchTab(Tab.DIALOGUES);
        }
    }

    private void updateDeleteButton() {
        if (this.deleteDialogueButton != null) { this.deleteDialogueButton.active = this.dialogueCheckboxes.values().stream().anyMatch(CheckboxWidget::isChecked); }
    }

    //render
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        //background texture
        Identifier guiTexture = Identifier.of("civiliansmod", "textures/gui/largergui.png");
        context.drawTexture(RenderPipelines.GUI_TEXTURED, guiTexture, containerX, containerY, 0, 0, containerWidth, containerHeight, containerWidth, containerHeight,0xFFFFFFFF);

        //vanilla render
        super.render(context, mouseX, mouseY, delta);

        //title and tab title
        context.drawText(this.textRenderer, this.title, this.containerX + 8, this.containerY + 8, 0x404040, false);
        context.drawText(this.textRenderer, this.currentTab.title, this.containerX + 120, this.containerY + 8, 0x404040, false);

        //big center NPC
        this.renderEntityPreview(context, mouseX, mouseY);

        //tab overlay
        switch (this.currentTab) {
            case SKINS -> this.renderSkinsTab(context, mouseX, mouseY);
            case DIALOGUES -> this.renderDialoguesTab(context, mouseX, mouseY);
            default -> {} // Other tabs do not need special rendering
        }
    }

    //skins tab
    private void renderSkinsTab(DrawContext context, int mouseX, int mouseY) {
        if (skinsToRender == null || skinsToRender.isEmpty()) {
            context.drawText(this.textRenderer, Text.literal("No skins found"),
                    containerX + 120, containerY + 50, 0xAAAAAA, false);
            return;
        }

        int contentX = containerX + 120, contentY = containerY + 50;

        for (int i = 0; i < skinsToRender.size(); i++) {
            int col = i % SKIN_COLUMNS;
            int row = i / SKIN_COLUMNS;

            int skinX = contentX + col * (SKIN_CELL_SIZE + SKIN_CELL_SPACING);
            int skinY = contentY + row * (SKIN_CELL_SIZE + SKIN_CELL_SPACING);

            int skinIndex = skinsToRender.get(i);
            renderVariantPreview(context, skinX, skinY, skinIndex, mouseX, mouseY);
        }
    }

    private void renderVariantPreview(DrawContext context, int x, int y, int skinIndex, int mouseX, int mouseY) {
        int width = SKIN_CELL_SIZE;
        int height = SKIN_CELL_SIZE;

        boolean selected = (skinIndex == this.selectedSkinIndex);
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;

        int backgroundColor = hovered ? 0x55FFFFFF : 0x33000000;
        int borderColor = selected ? 0xFFFFFFFF : 0xFFAAAAAA;

        // draw background
        context.fill(x, y, x + width, y + height, backgroundColor);

        // draw border
        int b = 1;
        context.fill(x, y, x + width, y + b, borderColor);
        context.fill(x, y + height - b, x + width, y + height, borderColor);
        context.fill(x, y, x + b, y + height, borderColor);
        context.fill(x + width - b, y, x + width, y + height, borderColor);

        // small NPC preview inside cell
        NPCEntity preview = getPreviewNPC(skinIndex);
        int centerX = x + width / 2;
        int centerY = y + height - 4;
        renderEntity(context, centerX, centerY, ENTITY_PREVIEW_SIZE, preview, false);
    }

    //dialogue tab render
    private void renderDialoguesTab(DrawContext context, int mouseX, int mouseY) {
        int contentX = containerX + 120, contentY = containerY + 75;
        int yPos = contentY - dialogueScrollOffset;

        //scrolling area
        context.enableScissor(contentX, contentY, contentX + 128, containerY + containerHeight - 55);
        for (CheckboxWidget checkbox : this.dialogueCheckboxes.values()) {
            checkbox.setX(contentX);
            checkbox.setY(yPos);
            checkbox.render(context, mouseX, mouseY, 0);
            yPos += 15;
        }
        context.disableScissor();
    }

    // mouseclick
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.currentTab == Tab.SKINS) {
            int contentX = containerX + 120, contentY = containerY + 50;

            for (int i = 0; i < skinsToRender.size(); i++) {
                int col = i % SKIN_COLUMNS;
                int row = i / SKIN_COLUMNS;

                int skinX = contentX + col * (SKIN_CELL_SIZE + SKIN_CELL_SPACING);
                int skinY = contentY + row * (SKIN_CELL_SIZE + SKIN_CELL_SPACING);

                int width = SKIN_CELL_SIZE;
                int height = SKIN_CELL_SIZE;

                if (mouseX >= skinX && mouseX < skinX + width &&  mouseY >= skinY && mouseY < skinY + height) {
                    int newSkinIndex = skinsToRender.get(i);
                    this.selectedSkinIndex = newSkinIndex;

                    // update center preview immediately
                    if (this.previewNpc != null) {
                        this.previewNpc = getPreviewNPC(newSkinIndex);
                    }
                    return true;
                }
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

    //ADD RENDERCORE

    @SuppressWarnings("unchecked")
    private void renderEntity(DrawContext context, int x, int y, int scale, LivingEntity entity, boolean isPreview) {
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

    //centerpreview logic

    private void renderEntityPreview(DrawContext context, int mouseX, int mouseY) {
        if (this.previewNpc == null) {
            this.previewNpc = getPreviewNPC(npc.getSkinManager().getBaseVariant());
        }

        NPCEntity preview = this.previewNpc;

        int variant = (selectedSkinIndex == -1)  ? npc.getSkinManager().getBaseVariant() : selectedSkinIndex;

        preview.getSkinManager().setIdSkin(NPCUtil.getNPCTexture(variant));
        preview.setAiDisabled(true);
        preview.setSilent(true);

        int px = containerX + 36;
        int py = containerY + (containerHeight / 2) + 34;

        float dx = (float)(mouseX - px);
        float dy = (float)(mouseY - py);

        float targetYaw = (-(float)Math.atan2(dx, 50.0) * (180F / (float)Math.PI)) / 2.0F;
        float targetPitch = ((float)Math.atan2(dy, 50.0) * (180F / (float)Math.PI)) / 2.0F;

        targetYaw   = MathHelper.clamp(targetYaw, -35.0F, 35.0F);
        targetPitch = MathHelper.clamp(targetPitch, -30.0F, 30.0F);

        smoothHeadYaw  += (targetYaw  - smoothHeadYaw) * 0.15F;
        smoothPitch    += (targetPitch - smoothPitch) * 0.15F;

        float bodyYaw = smoothHeadYaw * 0.1F;
        preview.setYaw(bodyYaw);
        preview.bodyYaw = bodyYaw;
        preview.setHeadYaw(smoothHeadYaw);
        preview.setPitch(smoothPitch);

        renderEntity(context, px, py, CENTER_PREVIEW_SIZE, preview, true);
    }

    //preview NPC creation

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

    private NPCEntity getPreviewNPC(int skinId) {
        if (previewNpcCache.containsKey(skinId)) {
            return previewNpcCache.get(skinId);
        }

        World world = MinecraftClient.getInstance().world;
        NPCEntity preview = new NPCEntity((EntityType<? extends PathAwareEntity>) npc.getType(), world);

        preview.getSkinManager().setIdSkin(NPCUtil.getNPCTexture(skinId));
        preview.setAiDisabled(true);
        preview.setSilent(true);
        preview.setHeadYaw(0.0F);

        previewNpcCache.put(skinId, preview);
        return preview;
    }

    private SkinIdentifier getSelectedSkin() {
        if (selectedSkinIndex < 0) {
            return npc.getSkinManager().getIdSkin();
        }
        return NPCUtil.getSkins().get(selectedSkinIndex);
    }

    // save and close
    private void saveAndClose() {
        ClientPlayNetworking.send(new NPCDataPayload(npc.getUuid(), nameInputField.getText(), stayState, followState, battleBuddyState, wanderRadiusState, dialogueOrderedState, tradePresetState));
        SkinIdentifier selected = getSelectedSkin();

        if (selectedSkinIndex == -1) {
            this.close();
            return;
        }

        byte[] data = NPCUtil.images.getOrDefault(selected, null);

        if (selected.custom()) {
            npc.getSkinManager().setSkinByteArray(data);
            npc.getSkinManager().setIdSkin(selected);
            npc.getSkinManager().setSlim(selected.slim());
            npc.getSkinManager().setDefaultSkin(false);
            ClientPlayNetworking.send(new ChangeSkinPayload( npc.getUuid(), selected.slim(), selected));
        } else {
            npc.getSkinManager().setBaseVariant(selectedSkinIndex);
            npc.getSkinManager().setSlim(selectedSkinIndex > 43);
            npc.getSkinManager().setIdSkin(NPCUtil.getNPCTexture(selectedSkinIndex));
            npc.getSkinManager().setDefaultSkin(true);
            ClientPlayNetworking.send(new ChangeBaseSkinPayload(npc.getUuid(), selectedSkinIndex));
        }
        npc.refreshSkinModel();
        this.close();
    }
}