package net.asian.civiliansmod.entity;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.goal.CustomDoorGoal;
import net.asian.civiliansmod.entity.goal.NPCAttackGoal;
import net.asian.civiliansmod.entity.goal.NPCDefendOwnerGoal;
import net.asian.civiliansmod.entity.goal.NPCFollowOwnerGoal;
import net.asian.civiliansmod.gui.CustomNPCScreen;
import net.asian.civiliansmod.gui.DefaultNPCScreen;
import net.asian.civiliansmod.gui.SlimNPCScreen;
import net.asian.civiliansmod.networking.payload.npc.dialogue.OpenScreenDialoguesPayload;
import net.asian.civiliansmod.networking.payload.npc.skin.ClientNpcSkinPayload;
import net.asian.civiliansmod.networking.payload.npc.skin.SyncSkinPayload;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.SkinIdentifier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


public class NPCEntity extends PathAwareEntity {

    private static final TrackedData<Boolean> IS_PAUSED = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> IS_FOLLOWING = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> IS_BATTLE_BUDDY = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    // FIX 1: Changed OPTIONAL_UUID to OPTIONAL_UNIQUE_ID
    private static final TrackedData<Optional<UUID>> OWNER_UUID = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private float originalMaxHealth = 20.0f;
    private static final TrackedData<Float> WANDER_RADIUS = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<BlockPos> WANDER_ANCHOR = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<String> TRADE_PRESET = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> DIALOGUE_ORDERED = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> DIALOGUE_INDEX = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.INTEGER);

    int updateDialoguesTicks = 0;
    private Set<UUID> sent;
    @Environment(EnvType.CLIENT)
    public boolean dialoguesReceived;
    public NameManager getNameManager() { return nameManager; }
    public SkinManager getSkinManager() { return skinManager; }
    public ChatManager getChatManager() { return chatManager; }
    NameManager nameManager = new NameManager(this);
    SkinManager skinManager = new SkinManager(this);
    ChatManager chatManager = new ChatManager(this);

    public NPCEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        if (!this.getWorld().isClient) {
            this.setCustomNameVisible(true);
            this.sent = new HashSet<>();
        } else {
            this.dialoguesReceived = false;
        }
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(IS_PAUSED, false);
        builder.add(IS_FOLLOWING, false);
        builder.add(IS_BATTLE_BUDDY, false);
        builder.add(OWNER_UUID, Optional.empty());
        builder.add(WANDER_RADIUS, 16.0f);
        builder.add(WANDER_ANCHOR, this.getBlockPos());
        builder.add(TRADE_PRESET, "none");
        builder.add(DIALOGUE_ORDERED, false);
        builder.add(DIALOGUE_INDEX, 0);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        // FIX 2: Changed attribute names to new format
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.ATTACK_DAMAGE, 3.0)
                .add(EntityAttributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.add(1, new NPCAttackGoal(this, 1.2D, true));
        this.goalSelector.add(2, new NPCFollowOwnerGoal(this, 1.0, 10.0f, 2.0f));
        this.goalSelector.add(3, new CustomDoorGoal(this));
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.7) {
            @Override public boolean canStart() { return !(isPaused() || isFollowing() || isBattleBuddy()) && super.canStart(); }
            @Nullable
            @Override
            protected Vec3d getWanderTarget() {
                BlockPos anchor = getWanderAnchor();
                Random random = NPCEntity.this.getRandom();
                double angle = random.nextFloat() * 2 * Math.PI;
                double radius = getWanderRadius() * Math.sqrt(random.nextFloat());
                double x = anchor.getX() + 0.5 + radius * Math.cos(angle);
                double z = anchor.getZ() + 0.5 + radius * Math.sin(angle);
                // FIX 3: Changed FuzzyTargeting.find to FuzzyTargeting.findTo
                return FuzzyTargeting.findTo(this.mob, 10, 7, new Vec3d(x, this.mob.getY(), z));
            }
        });
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(6, new LookAroundGoal(this));
        this.targetSelector.add(1, new NPCDefendOwnerGoal(this));
        this.targetSelector.add(2, new RevengeGoal(this).setGroupRevenge());
    }

    @Override
    protected void writeCustomData(WriteView writeView) {
        super.writeCustomData(writeView);
        writeView.putBoolean("IsPaused", this.isPaused());
        writeView.putBoolean("IsFollowing", this.isFollowing());
        this.getOwnerUuid().ifPresent(uuid -> writeView.put("Owner", Uuids.CODEC, uuid));
        writeView.putBoolean("IsBattleBuddy", this.isBattleBuddy());
        writeView.putFloat("WanderRadius", this.getWanderRadius());
        writeView.put("WanderAnchor", BlockPos.CODEC, this.getWanderAnchor());
        writeView.putString("TradePreset", this.getTradePreset());
        writeView.putBoolean("DialogueOrdered", this.isDialogueOrdered());
        writeView.putInt("DialogueIndex", this.getDialogueIndex());
    }

    @Override
    protected void readCustomData(ReadView readView) {
        super.readCustomData(readView);
        this.setPaused(readView.getBoolean("IsPaused", false));
        this.setFollowing(readView.getBoolean("IsFollowing", false));
        readView.read("Owner", Uuids.CODEC).ifPresent(this::setOwnerUuid);
        this.setBattleBuddy(readView.getBoolean("IsBattleBuddy", false));
        this.setWanderRadius(readView.getFloat("WanderRadius", 16.0f));
        this.setWanderAnchor(readView.read("WanderAnchor", BlockPos.CODEC).orElse(this.getBlockPos()));
        this.setTradePreset(readView.getString("TradePreset", "none"));
        this.setDialogueOrdered(readView.getBoolean("DialogueOrdered", false));
        this.setDialogueIndex(readView.getInt("DialogueIndex", 0));
    }

    public boolean isPaused() { return this.dataTracker.get(IS_PAUSED); }
    public void setPaused(boolean paused) {
        if (paused) { this.setWanderAnchor(this.getBlockPos()); }
        this.dataTracker.set(IS_PAUSED, paused);
    }
    public boolean isFollowing() { return this.dataTracker.get(IS_FOLLOWING); }
    public void setFollowing(boolean following) { this.dataTracker.set(IS_FOLLOWING, following); }
    public boolean isBattleBuddy() { return this.dataTracker.get(IS_BATTLE_BUDDY); }
    public void setBattleBuddy(boolean battleBuddy) {
        this.dataTracker.set(IS_BATTLE_BUDDY, battleBuddy);
        if (!this.getWorld().isClient) {
            if (battleBuddy) {
                this.originalMaxHealth = this.getMaxHealth();
                // FIX 4: Changed GENERIC_MAX_HEALTH to MAX_HEALTH
                Objects.requireNonNull(this.getAttributeInstance(EntityAttributes.MAX_HEALTH)).setBaseValue(40.0D);
                this.heal(40.0f);
            } else {
                // FIX 5: Changed GENERIC_MAX_HEALTH to MAX_HEALTH
                Objects.requireNonNull(this.getAttributeInstance(EntityAttributes.MAX_HEALTH)).setBaseValue(this.originalMaxHealth);
                if (this.getHealth() > this.originalMaxHealth) { this.setHealth(this.originalMaxHealth); }
            }
        }
    }
    public Optional<UUID> getOwnerUuid() { return this.dataTracker.get(OWNER_UUID); }
    public void setOwnerUuid(@Nullable UUID uuid) { this.dataTracker.set(OWNER_UUID, Optional.ofNullable(uuid)); }
    @Nullable
    public LivingEntity getOwner() {
        try {
            UUID uUID = this.getOwnerUuid().orElse(null);
            return uUID == null ? null : this.getWorld().getPlayerByUuid(uUID);
        } catch (IllegalArgumentException e) { return null; }
    }
    public void setOwner(PlayerEntity player) { this.setOwnerUuid(player.getUuid()); }
    public float getWanderRadius() { return this.dataTracker.get(WANDER_RADIUS); }
    public void setWanderRadius(float radius) { this.dataTracker.set(WANDER_RADIUS, MathHelper.clamp(radius, 4.0f, 64.0f)); }
    public BlockPos getWanderAnchor() { return this.dataTracker.get(WANDER_ANCHOR); }
    public void setWanderAnchor(BlockPos pos) { this.dataTracker.set(WANDER_ANCHOR, pos); }
    public String getTradePreset() { return this.dataTracker.get(TRADE_PRESET); }
    public void setTradePreset(String preset) { this.dataTracker.set(TRADE_PRESET, preset); }
    public boolean isDialogueOrdered() { return this.dataTracker.get(DIALOGUE_ORDERED); }
    public void setDialogueOrdered(boolean ordered) { this.dataTracker.set(DIALOGUE_ORDERED, ordered); }
    public int getDialogueIndex() { return this.dataTracker.get(DIALOGUE_INDEX); }
    public void setDialogueIndex(int index) { this.dataTracker.set(DIALOGUE_INDEX, index); }
    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient || hand != Hand.MAIN_HAND) { return ActionResult.PASS; }
        ItemStack heldItem = player.getStackInHand(hand);
        if (heldItem.isOf(Items.LEAD) && this.canBeLeashedBy(player)) {
            this.attachLeash(player, true);
            return ActionResult.SUCCESS;
        }
        if (player.isSneaking()) {
            this.getNavigation().stop();
            this.getLookControl().lookAt(player, 30.0f, 30.0f);
            if (player instanceof ServerPlayerEntity serverPlayer) {
                OpenScreenDialoguesPayload payload = new OpenScreenDialoguesPayload(this.getId(), this.getChatManager().getDialogues());
                ServerPlayNetworking.send(serverPlayer, payload);
            }
            return ActionResult.SUCCESS;
        } else {
            this.getNavigation().stop();
            Text nameText = this.getCustomName();
            String npcName = nameText != null ? nameText.getString() : "NPC";
            String dialogue;
            String playerLanguage = CiviliansMod.playerLanguages.getOrDefault(player.getUuid(), "en_us");
            if (isDialogueOrdered()) {
                dialogue = getChatManager().getOrderedChat(playerLanguage, NpcChat.ChatReason.INTERACT, getDialogueIndex());
                setDialogueIndex(getDialogueIndex() + 1);
            } else {
                dialogue = getChatManager().getRandomChat(playerLanguage, NpcChat.ChatReason.INTERACT);
            }
            player.sendMessage(Text.literal("<" + npcName + "> " + dialogue), false);
            return ActionResult.SUCCESS;
        }
    }
    @Override
    public boolean cannotDespawn() { return true; }
    @Environment(EnvType.CLIENT)
    public void openCustomNPCScreen() {
        SkinManager sm = getSkinManager();
        if (sm == null) return;
        if (sm.isSlimModel() && sm.isDefaultSkin()) {
            MinecraftClient.getInstance().setScreen(new SlimNPCScreen(this));
        } else if (sm.isDefaultSkin()) {
            MinecraftClient.getInstance().setScreen(new DefaultNPCScreen(this));
        } else {
            MinecraftClient.getInstance().setScreen(new CustomNPCScreen(this));
        }
    }
    @Override
    public Vec3d getLeashOffset() { return new Vec3d(0.0, 0.9, 0.0); }
    public boolean canBeLeashedBy(PlayerEntity player) { return !this.isLeashed() && !this.isBattleBuddy(); }
    public boolean hasSentTo(UUID playerId) { return sent != null && sent.contains(playerId); }
    public void markSentTo(UUID playerId) { if (sent != null) { sent.add(playerId); } }
    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        if (this.getWorld().isClient) {
            SkinIdentifier skinIdentifier = NPCUtil.waitingSync.get(this.getId());
            if (skinIdentifier != null) { this.skinManager.setIdSkin(skinIdentifier); }
            updateDialoguesTicks = 10;
        }
    }
    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
        if (!this.getWorld().isClient) {
            if (this.skinManager.getSkinByteArray() == null) {
                for (ServerPlayerEntity player : Objects.requireNonNull(this.getWorld().getServer()).getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(player, new SyncSkinPayload(this.getId(), this.skinManager.getBaseVariant()));
                }
            } else {
                for (ServerPlayerEntity player : Objects.requireNonNull(this.getWorld().getServer()).getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(player, new ClientNpcSkinPayload(this.getId(), this.skinManager.isSlimModel(), this.skinManager.getSkinByteArray()));
                }
            }
        }
        return super.createSpawnPacket(entityTrackerEntry);
    }
}