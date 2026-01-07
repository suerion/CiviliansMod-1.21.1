package net.asian.civiliansmod.gui;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.gui.widgets.CheckboxWidget;
import net.asian.civiliansmod.gui.widgets.DialogueListWidget;
import net.asian.civiliansmod.gui.widgets.TextButtonWidget;
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

    //refactore GUI positions
    private static final int TABS_X = 10;
    private static final int TABS_Y = 8;
    private static final int TAB_WIDTH = 64;
    private static final int TAB_HEIGHT = 13;
    private static final int TAB_SPACING = 4;

    private static final int CUSTOMBUTTON_HEIGHT = 13;

    private static final int PREVIEW_X = 16;
    private static final int PREVIEW_Y = 47;
    private static final int PREVIEW_W = 64;
    private static final int PREVIEW_H = 110;
    private static final int CENTER_PREVIEW_SIZE = 35; // render center preview

    private static final int NAME_X = 10;
    private static final int NAME_Y = PREVIEW_Y - 12;
    private static final int NAME_W = 55;
    private static final int NAME_H = 18;

    // constants for small preview layout
    private static final int SKIN_CELL_SPACING = 1;
    private static final int SKIN_CELL_W = 38;
    private static final int SKIN_CELL_H = 56;
    private static final int SKIN_COLUMNS = 3;
    private static final int GRID_X = 83;
    private static final int GRID_Y = 39;
    private static final int GRID_W = (SKIN_COLUMNS * SKIN_CELL_W) + ((SKIN_COLUMNS - 1) * SKIN_CELL_SPACING);
    private static final int GRID_H = (2 * SKIN_CELL_H) + ((2 - 1) * SKIN_CELL_SPACING);
    private static final int ENTITY_PREVIEW_SIZE = 25; // small NPCs
    private double skinScrollY = 0;
    private double maxSkinScrollY = 0;

    //constante for buttons
    private static final int BTN_SKIN_X = GRID_X + GRID_W;
    private static final int BTN_SKIN_Y = GRID_Y;
    private static final int BTN_SKIN_W = 40;
    private static final int BTN_SKIN_H = 13;
    private static final int BTN_SKIN_SPACING = 6;

    private static final int BTN_CUSTOM_WIDTH = 52;
    private static final int BTN_CUSTOM_Y_OFFSET = (BTN_SKIN_H + BTN_SKIN_SPACING) * 2;

    private int DIALOG_X;
    private int DIALOG_Y;
    private int DIALOG_W;
    private int DIALOG_H;

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

    private DialogueListWidget dialogueList;
    private boolean selectionMode = false;

    //AI state
    private boolean battleBuddyState, stayState, followState, dialogueOrderedState;
    private float wanderRadiusState;
    private boolean previousStay, previousFollow, previousBB;

    //Trade State
    private String tradePresetState;

    //Render Core
    private NPCEntity previewNpc;

    //smooth head rotation
    private float smoothHeadYaw = 0.0F;
    private float smoothPitch = 0.0F;

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

    private void updateWanderAnchorCheck() {
        boolean nowWander = !stayState && !followState && !battleBuddyState;
        boolean previouslyNotWander = previousStay || previousFollow || previousBB;

        if (nowWander && previouslyNotWander) {
            npc.setWanderAnchor(npc.getBlockPos());
        }

        // Update old state
        previousStay = stayState;
        previousFollow = followState;
        previousBB = battleBuddyState;
    }

    @Override
    protected void init() {
        //safe old states
        this.previousStay = this.stayState;
        this.previousFollow = this.followState;
        this.previousBB = this.battleBuddyState;

        SkinIdentifier id = npc.getSkinManager().getIdSkin();
        this.originalVariantIndex = NPCUtil.getSkins().indexOf(id);
        this.selectedSkinIndex = this.originalVariantIndex;
        CiviliansMod.LOGGER.info(
                "[GUI-OPEN] npc={} originalVariant={} selectedVariant={} skinId={}",
                npc.getUuid(),
                this.originalVariantIndex,
                this.selectedSkinIndex,
                id
        );
        previewNpcCache.clear();
        super.init();

        this.wanderRadiusState = npc.getWanderRadius();

        //background box
        this.containerWidth = 286;
        this.containerHeight = 191;
        this.containerX = (this.width - this.containerWidth) / 2;
        this.containerY = (this.height - this.containerHeight) / 2;

        this.DIALOG_X = this.containerX + 69;
        this.DIALOG_Y = this.containerY + 38;
        this.DIALOG_W = 208;
        this.DIALOG_H = 114;

        // center preview NPC
        if (this.client != null && this.client.world != null && this.previewNpc == null) {
            this.previewNpc = this.createPreviewNPCFromNPC(npc);
        }

        // name field
        String currentName = npc.getCustomName() != null ? npc.getCustomName().getString() : "";
        this.nameInputField = new TextFieldWidget(this.textRenderer, containerX + NAME_X, containerY + NAME_Y, NAME_W, NAME_H, Text.empty());
        this.nameInputField.setText(currentName);
        this.nameInputField.setMaxLength(32);
        this.addSelectableChild(this.nameInputField);

        //buttons bottom
        /*
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), b -> this.saveAndClose()).dimensions(containerX + containerWidth - 88, containerY + containerHeight - 28, 80, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> this.close()).dimensions(containerX + 8, containerY + containerHeight - 28, 80, 20).build());
*/
        this.addDrawableChild(new TextButtonWidget(containerX + containerWidth - 88, containerY + containerHeight - 25, 80, CUSTOMBUTTON_HEIGHT, Text.literal("Save & Close"), b -> this.saveAndClose()));
        this.addDrawableChild(new TextButtonWidget(containerX + 8, containerY + containerHeight - 25, 80, CUSTOMBUTTON_HEIGHT, Text.literal("Cancel"), b -> this.close()));

        int TABAREAX = containerX + TABS_X;
        int TABAREAWIDTH = containerWidth - (TABS_X * 2);
        int tabStartY = containerY + TABS_Y;

        int tabCount = Tab.values().length;
        int totalSpacing = (tabCount - 1) * TAB_SPACING;
        int availableWidth = TABAREAWIDTH - totalSpacing;
        int dynamicTabWidth = availableWidth / tabCount;
        if (dynamicTabWidth < 40) dynamicTabWidth = 40;

        int totalWidth = (tabCount * dynamicTabWidth) + ((tabCount - 1) * TAB_SPACING);
        int startX = TABAREAX + (TABAREAWIDTH - totalWidth) / 2;

        /*

        this.addDrawableChild(ButtonWidget.builder(Tab.SKINS.title, b -> this.switchTab(Tab.SKINS)).dimensions(tabStartX, tabStartY, TAB_WIDTH, TAB_HEIGHT).build());
        this.addDrawableChild(ButtonWidget.builder(Tab.AI.title, b -> this.switchTab(Tab.AI)).dimensions(tabStartX + (TAB_WIDTH + TAB_SPACING), tabStartY, TAB_WIDTH, TAB_HEIGHT).build());
        this.addDrawableChild(ButtonWidget.builder(Tab.DIALOGUES.title, b -> this.switchTab(Tab.DIALOGUES)).dimensions(tabStartX + 2 * (TAB_WIDTH + TAB_SPACING), tabStartY, TAB_WIDTH, TAB_HEIGHT).build());
        this.addDrawableChild(ButtonWidget.builder(Tab.TRADES.title, b -> this.switchTab(Tab.TRADES)).dimensions(tabStartX + 3 * (TAB_WIDTH + TAB_SPACING), tabStartY, TAB_WIDTH, TAB_HEIGHT).build());

        */

        int tx = startX;

        for (int i = 0; i < tabCount; i++) {
            Tab t = Tab.values()[i];

            int tabColor = 0xFFFFFF;
            boolean active = (t == currentTab);

            this.addDrawableChild(new TextButtonWidget(tx, tabStartY, dynamicTabWidth, TAB_HEIGHT, t.title, b -> this.switchTab(t) ,tabColor));
            tx += dynamicTabWidth + TAB_SPACING;
        }
        //tab widgets
        this.initTabWidgets();
    }

    private void switchTab(Tab newTab) {
        if (this.currentTab != newTab) {
            this.tabToSwitchTo = newTab;
            this.pendingTabSwitch = true;
        }
    }

    public void openDialoguesTab() {
        this.currentTab = Tab.DIALOGUES;
        this.clearAndInit();

        if (this.dialogueList != null) {
            this.dialogueList.reloadFromNPC();
        }
    }

    private void toggleSelection() {
        selectionMode = !selectionMode;
        if (dialogueList != null) {
            dialogueList.setSelectionMode(selectionMode);
        }
    }

    private void deleteSelected() {
        if (dialogueList == null) return;

        List<String> selected = dialogueList.getSelectedDialogues();
        if (selected.isEmpty()) return;

        String lang = MinecraftClient.getInstance().getLanguageManager().getLanguage();

        for (NpcChat.ChatReason reason : NpcChat.ChatReason.values()) {
            List<String> all = dialogueList.getAllDialoguesFor(reason);
            List<String> toRemove = selected.stream()
                    .filter(all::contains)
                    .toList();

            if (!toRemove.isEmpty()) {
                ClientPlayNetworking.send(new MassRemoveDialoguePayload(
                        npc.getUuid(), lang, reason, toRemove
                ));
            }
        }

        dialogueList.deleteLocal(selected);
        dialogueList.reloadFromNPC();
    }

    private void deleteAll() {
        if (dialogueList == null) return;

        String lang = MinecraftClient.getInstance().getLanguageManager().getLanguage();

        for (NpcChat.ChatReason reason : NpcChat.ChatReason.values()) {

            if (!reason.isActive()) continue;
            List<String> list = dialogueList.getAllDialoguesFor(reason);
            if (!list.isEmpty()) {
                ClientPlayNetworking.send(new MassRemoveDialoguePayload(npc.getUuid(), lang, reason, list
                ));
            }
        }

        dialogueList.deleteAllLocal();
        dialogueList.reloadFromNPC();
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

        int contentX = containerX + GRID_X;
        int contentY = containerY + GRID_Y;

        int contentWidth = 128;
        switch (this.currentTab) {
            case SKINS -> {

                int topX = containerX + GRID_X - 12;
                int topY = containerY + GRID_Y - BTN_SKIN_H - 3;

                // active colors
                boolean isWide   = this instanceof DefaultNPCScreen;
                boolean isSlim   = this instanceof SlimNPCScreen;
                boolean isCustom = this instanceof CustomNPCScreen;

                int wideColor   = isWide   ? 0x00FF00 : 0xFFFFFF;
                int slimColor   = isSlim   ? 0x00FF00 : 0xFFFFFF;
                int customColor = isCustom ? 0x00FF00 : 0xFFFFFF;

                //skins from cubclass
                List<Integer> list = this.getSkinsToRender();
                this.skinsToRender = (list != null) ? list : Collections.emptyList();

                //skin type switch buttons
                this.addDrawableChild(new TextButtonWidget(topX, topY, BTN_SKIN_W, BTN_SKIN_H, Text.literal("Wide"), btn -> this.client.setScreen(new DefaultNPCScreen(this.npc)),wideColor));
                this.addDrawableChild(new TextButtonWidget(topX + BTN_SKIN_W, topY, BTN_SKIN_W, BTN_SKIN_H, Text.literal("Slim"), btn -> this.client.setScreen(new SlimNPCScreen(this.npc)), slimColor));
                this.addDrawableChild(new TextButtonWidget(topX + (BTN_SKIN_W) * 2, topY, BTN_CUSTOM_WIDTH, BTN_SKIN_H, Text.literal("Custom"), btn -> this.client.setScreen(new CustomNPCScreen(this.npc)), customColor));
            }
            case AI -> {
                this.wanderRadiusState = npc.getWanderRadius();
                int x = containerX + BTN_SKIN_X;
                int y = contentY;

                final CheckboxWidget[] stayCheckbox = new CheckboxWidget[1];
                final CheckboxWidget[] followCheckbox = new CheckboxWidget[1];
                final CheckboxWidget[] battleBuddyCheckbox = new CheckboxWidget[1];

                //FOLLOW
                followCheckbox[0] = new CheckboxWidget(x, y +25, 100, 20, Text.literal("Follow"), this.followState, (checked) -> {
                    if (this.followState != checked) {
                        this.followState = checked;
                    }
                    if (checked) {
                        //follow on, stay off
                        this.stayState = false;
                        stayCheckbox[0].setChecked(false);
                        //now the battlebuddycheckbox is activated
                        battleBuddyCheckbox[0].active = true;
                    } else {
                        //follow off, battlebuddy should not activated
                        if (this.battleBuddyState) {
                            this.battleBuddyState = false;
                            battleBuddyCheckbox[0].setChecked(false);
                        }
                        battleBuddyCheckbox[0].active = false;
                    }
                    updateWanderAnchorCheck();
                    //if stay and follow disable, wander slider should activated
                    this.clearAndInit();
                    }
                );

                //STAY
                stayCheckbox[0] = new CheckboxWidget(x, y, 100, 20, Text.literal("Stay"), this.stayState, (checked) -> {
                    if (this.stayState != checked) {
                        this.stayState = checked;
                    }
                    if (checked) {
                        //stay on, follow off
                        this.followState = false;
                        followCheckbox[0].setChecked(false);

                        //battlebuddy should not activated
                        if (this.battleBuddyState) {
                            this.battleBuddyState = false;
                            battleBuddyCheckbox[0].setChecked(false);
                        }
                        battleBuddyCheckbox[0].active = false;
                    } else {
                        // Stay off , if follow activated, battlebuddy could activated
                        battleBuddyCheckbox[0].active = this.followState;
                    }
                    updateWanderAnchorCheck();
                    this.clearAndInit();
                });

                followCheckbox[0].active = !this.stayState;
                stayCheckbox[0].active = !this.followState;

                this.addDrawableChild(stayCheckbox[0]);
                this.addDrawableChild(followCheckbox[0]);

                battleBuddyCheckbox[0] =new CheckboxWidget(x, y + 50, 100, 20, Text.literal("Battle Buddy"), this.battleBuddyState, (checked) -> {
                    this.battleBuddyState = checked;

                    if (checked) {
                        // battlebuddy only on follow, strict follow activated
                        this.followState = true;
                        this.stayState = false;

                        followCheckbox[0].setChecked(true);
                        stayCheckbox[0].setChecked(false);

                        // if follow, stay are not activated
                        stayCheckbox[0].active = false;
                    } else {
                        // battlebuddy off
                        // follow should be follow
                        stayCheckbox[0].active = !this.followState;
                    }
                    updateWanderAnchorCheck();
                    this.clearAndInit();
                });

                //battle buddy only clickable if floow activated
                battleBuddyCheckbox[0].active = this.followState;
                this.addDrawableChild(battleBuddyCheckbox[0]);

                //wanderslider only if no stay, no follow, no battlebuddy
                if (!this.stayState && !this.followState && !this.battleBuddyState) {
                    double sliderValue = MathHelper.clamp((wanderRadiusState - 4f) / 60f, 0.0, 1.0);
                    SliderWidget wanderSlider =  new SliderWidget(x - 5, y + 80,contentWidth, 20, Text.literal("Wander: " + (int) wanderRadiusState),sliderValue) {
                        @Override
                        protected void updateMessage() {
                            //only current mapped value
                            setMessage(Text.literal("Wander: " + (int) getMappedValue()));
                        }

                        @Override
                        protected void applyValue() {
                            wanderRadiusState = (float) getMappedValue();
                        }

                        private double getMappedValue() {
                            return 4.0 + this.value * 60.0;
                        }
                    };
                    this.addDrawableChild(wanderSlider);
                }
            }
            case DIALOGUES -> {
                this.dialogueList = new DialogueListWidget(npc, DIALOG_X, DIALOG_Y, DIALOG_W, DIALOG_H);

                int bx = containerX + 75;
                int by = containerY + 155;

                //toogle selection mode
                this.addDrawableChild(ButtonWidget.builder(Text.literal(selectionMode ? "Exit Select" : "Select Mode"),btn -> {
                    toggleSelection();
                    btn.setMessage(Text.literal(selectionMode ? "Exit Select" : "Select Mode"));
                }).dimensions(bx, by, 90, 20).build());

                //delete selected
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Delete Selected"),btn -> {
                    deleteSelected();
                    // Refresh list after deletion
                    if (dialogueList != null) dialogueList.reloadFromNPC();
                }).dimensions(bx + 95, by, 110, 20).build());

                //delete all
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Delete All"), btn -> {
                    deleteAll();
                    if (dialogueList != null) dialogueList.reloadFromNPC();
                }).dimensions(bx + 210, by, 80, 20).build());
            }
        }
    }

    //abstract method for subclasses to define which skin indices they want to render
    protected abstract List<Integer> getSkinsToRender();

    //render
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        //background texture
        Identifier guiTexture =
                (this.currentTab == Tab.DIALOGUES)
                        ? Identifier.of("civiliansmod", "textures/gui/largerdialogue.png")
                        : Identifier.of("civiliansmod", "textures/gui/largergui.png");
        context.drawTexture(RenderPipelines.GUI_TEXTURED, guiTexture, containerX, containerY, 0, 0, containerWidth, containerHeight, containerWidth, containerHeight,0xFFFFFFFF);

        //vanilla render
        super.render(context, mouseX, mouseY, delta);

        // name field
        this.nameInputField.render(context, mouseX, mouseY, delta);

        //title and tab title
        context.drawText(this.textRenderer, this.title, this.containerX + 8, this.containerY + 8, 0x404040, false);
        context.drawText(this.textRenderer, this.currentTab.title, this.containerX + 120, this.containerY + 8, 0x404040, false);

        //big center NPC
        this.renderEntityPreview(context, mouseX, mouseY);

        if (currentTab == Tab.DIALOGUES && dialogueList != null) {
            dialogueList.renderWidget(context, mouseX, mouseY, delta);
        }

        //tab overlay
        switch (this.currentTab) {
            case SKINS -> this.renderSkinsTab(context, mouseX, mouseY);
            case DIALOGUES -> {}
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

        int viewLeft   = containerX + GRID_X;
        int viewTop    = containerY + GRID_Y;
        int viewRight  = viewLeft + GRID_W;
        int viewBottom = viewTop + GRID_H;

        int contentX = viewLeft;
        int contentY = viewTop;

        context.enableScissor(viewLeft, viewTop, viewRight, viewBottom);

        int totalRows = (int)Math.ceil(skinsToRender.size() / (double)SKIN_COLUMNS);
        int contentHeight = totalRows * (SKIN_CELL_H + SKIN_CELL_SPACING);

        maxSkinScrollY = Math.max(0, contentHeight - GRID_H);
        skinScrollY = MathHelper.clamp(skinScrollY, 0, maxSkinScrollY);

        for (int i = 0; i < skinsToRender.size(); i++) {
            int col = i % SKIN_COLUMNS;
            int row = i / SKIN_COLUMNS;

            int skinX = contentX + col * (SKIN_CELL_W + SKIN_CELL_SPACING);
            int skinY = contentY + row * (SKIN_CELL_H + SKIN_CELL_SPACING) - (int) skinScrollY;

            int skinIndex = skinsToRender.get(i);

            int cellBottom = skinY + SKIN_CELL_H;
            int cellTop = skinY;
            if (cellBottom < viewTop || cellTop > viewBottom) {
                continue;
            }
            renderVariantPreview(context, skinX, skinY, skinIndex, mouseX, mouseY);
        }
        context.disableScissor();
        renderSkinScrollbar(context);
    }

    private void renderSkinScrollbar(DrawContext context) {

        if (maxSkinScrollY <= 0) return;

        int barWidth = 7;

        int barX = containerX + GRID_X -12;
        int barY = containerY + GRID_Y;
        int barHeight = GRID_H;

        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0x22000000);

        float ratio = (float)(GRID_H / (float)(maxSkinScrollY + GRID_H));
        int thumbHeight = Math.max(24, (int)(GRID_H * ratio));

        int thumbY = barY + (int)((skinScrollY / maxSkinScrollY) * (GRID_H - thumbHeight));

        double mx = MinecraftClient.getInstance().mouse.getX() / MinecraftClient.getInstance().getWindow().getScaleFactor();
        double my = MinecraftClient.getInstance().mouse.getY() / MinecraftClient.getInstance().getWindow().getScaleFactor();

        boolean hovered =
                mx >= barX && mx <= barX + barWidth &&
                        my >= thumbY && my <= thumbY + thumbHeight;

        int thumbColor = hovered ? 0xFFFFFFFF : 0xFF999999;

        context.fill(barX + 1, thumbY + 1, barX + barWidth - 1, thumbY + thumbHeight - 1, thumbColor);
    }
    private void renderVariantPreview(DrawContext context, int x, int y, int skinIndex, int mouseX, int mouseY) {
        int width = SKIN_CELL_W;
        int height = SKIN_CELL_H;

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
        int centerY = y + height - 3;
        renderEntity(context, centerX, centerY, ENTITY_PREVIEW_SIZE, preview, false);
    }

    // mouseclick
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.currentTab == Tab.SKINS) {
            int contentX = containerX + GRID_X;
            int contentY = containerY + GRID_Y;

            for (int i = 0; i < skinsToRender.size(); i++) {
                int col = i % SKIN_COLUMNS;
                int row = i / SKIN_COLUMNS;

                int skinX = contentX + col * (SKIN_CELL_W + SKIN_CELL_SPACING);
                int skinY = contentY + row * (SKIN_CELL_H + SKIN_CELL_SPACING) - (int) skinScrollY;

                int width = SKIN_CELL_W;
                int height = SKIN_CELL_H;

                if (mouseX >= skinX && mouseX < skinX + width &&  mouseY >= skinY && mouseY < skinY + height) {
                    int newSkinIndex = skinsToRender.get(i);
                    this.selectedSkinIndex = newSkinIndex;

                    CiviliansMod.LOGGER.info(
                            "[GUI-SELECT] npc={} clickedVariant={}",
                            npc.getUuid(),
                            newSkinIndex
                    );

                    // update center preview immediately
                    this.previewNpc = previewNpcCache.computeIfAbsent(newSkinIndex, this::createPreviewNPC);
                    return true;
                }
            }
        }
        if (currentTab == Tab.DIALOGUES && dialogueList != null) {
            if (dialogueList.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Skin scrolling
        if (this.currentTab == Tab.SKINS) {
            int totalRows = (int) Math.ceil((double) skinsToRender.size() / SKIN_COLUMNS);
            int contentHeight = totalRows * (SKIN_CELL_H + SKIN_CELL_SPACING);

            skinScrollY = MathHelper.clamp(
                    skinScrollY - verticalAmount * 10,
                    0,
                    maxSkinScrollY
            );
            return true;
        }
        if (currentTab == Tab.DIALOGUES && dialogueList != null) {
            if (dialogueList.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
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

        if (state instanceof net.minecraft.client.render.entity.state.LivingEntityRenderState s) {
            if (isPreview) {
                float headYaw = entity.headYaw;
                float bodyYaw = headYaw * 0.1F;
                s.bodyYaw = bodyYaw;
                s.relativeHeadYaw = headYaw - bodyYaw;
                s.pitch = entity.getPitch();
            }
            if (!isPreview) {
                s.bodyYaw = 0.0F;
                s.relativeHeadYaw = 0.0F;
                s.pitch = 0.0F;

                s.limbSwingAnimationProgress = 0.0F;
                s.limbSwingAmplitude = 0.0F;
                s.deathTime = 0.0F;
                s.shaking = false;
                s.hurt = false;
                s.touchingWater = false;
                s.usingRiptide = false;
            }
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
            this.previewNpc = createPreviewNPCFromNPC(npc);
        }

        NPCEntity preview = this.previewNpc;

        preview.setAiDisabled(true);
        preview.setSilent(true);

        int px = containerX + PREVIEW_X + PREVIEW_W / 2-10;
        int py = containerY + PREVIEW_Y + PREVIEW_H - 10-18;

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

        SkinIdentifier id = NPCUtil.getNPCTexture(skinId);
        preview.getSkinManager().setIdSkin(id);

        if (id.custom()) {
            byte[] data = npc.getSkinManager().getSkinByteArray();
            if (data != null) preview.getSkinManager().setSkinByteArray(data);
            preview.getSkinManager().setDefaultSkin(false);
        } else {
            preview.getSkinManager().setDefaultSkin(true);
        }

        preview.refreshSkinModel();
        preview.setAiDisabled(true);
        preview.setSilent(true);
        preview.setHeadYaw(0.0F);

        return preview;
    }

    private NPCEntity createPreviewNPCFromNPC(NPCEntity npc) {
        World world = MinecraftClient.getInstance().world;
        NPCEntity preview = new NPCEntity((EntityType<? extends PathAwareEntity>) npc.getType(), world);

        SkinIdentifier id = npc.getSkinManager().getIdSkin();

        // Set identifier (Slim + Path)
        preview.getSkinManager().setIdSkin(id);

        // Custom skin data must be re-applied manually
        if (id.custom()) {
            byte[] data = npc.getSkinManager().getSkinByteArray();
            if (data != null) {
                preview.getSkinManager().setSkinByteArray(data);
            }
            preview.getSkinManager().setDefaultSkin(false);
        } else {
            preview.getSkinManager().setDefaultSkin(true);
        }

        preview.refreshSkinModel();
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

        SkinIdentifier id = NPCUtil.getNPCTexture(skinId);
        preview.getSkinManager().setIdSkin(id);

        if (id.custom()) {
            byte[] data = npc.getSkinManager().getSkinByteArray();
            if (data != null) preview.getSkinManager().setSkinByteArray(data);
            preview.getSkinManager().setDefaultSkin(false);
        } else {
            preview.getSkinManager().setDefaultSkin(true);
        }

        preview.refreshSkinModel();
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

        updateWanderAnchorCheck();

        if (selectedSkinIndex == -1) {
            this.close();
            return;
        }

        CiviliansMod.LOGGER.info("[GUI-SAVE] npc={} originalVariant={} selectedVariant={} isCustom={}", npc.getUuid(), originalVariantIndex, selectedSkinIndex, selected.custom());

        if (selected.custom()) {
            byte[] bytes = NPCUtil.images.get(selected);
            boolean slim = selected.slim();

            if (bytes == null) {
                CiviliansMod.LOGGER.warn("[GUI-SAVE] Custom skin selected but no skin bytes present npc={}", npc.getUuid());
                this.close();
                return;
            }
            CiviliansMod.LOGGER.info("[GUI] Save custom skin npc={} custom={} bytes={}", npc.getUuid(), slim, npc.getSkinManager().getSkinByteArray() == null ? -1 : npc.getSkinManager().getSkinByteArray().length);

            ClientPlayNetworking.send(new ChangeSkinPayload(npc.getUuid(), slim, bytes));

            npc.getSkinManager().setSkinByteArray(bytes);
            npc.getSkinManager().setIdSkin(new SkinIdentifier(Identifier.of(CiviliansMod.MOD_ID, "npc_skin_" + npc.getUuid()), slim, true));
            npc.getSkinManager().setDefaultSkin(false);
            npc.refreshSkinModel();

        } else {
            if (selectedSkinIndex != originalVariantIndex) {
                npc.setTrackedSkinVariant(selectedSkinIndex);
                npc.getSkinManager().setBaseVariant(selectedSkinIndex);
            }
            npc.getSkinManager().setIdSkin(NPCUtil.getNPCTexture(selectedSkinIndex));
            npc.getSkinManager().setDefaultSkin(true);
            ClientPlayNetworking.send(new ChangeBaseSkinPayload(npc.getUuid(), selectedSkinIndex));
        }
        npc.refreshSkinModel();
        this.close();
    }
}