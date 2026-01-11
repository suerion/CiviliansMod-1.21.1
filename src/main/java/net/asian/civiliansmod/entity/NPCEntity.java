package net.asian.civiliansmod.entity;

import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.goal.*;
import net.asian.civiliansmod.gui.CustomNPCScreen;
import net.asian.civiliansmod.gui.DefaultNPCScreen;
import net.asian.civiliansmod.gui.SlimNPCScreen;
import net.asian.civiliansmod.networking.payload.npc.dialogue.OpenScreenDialoguesPayload;
import net.asian.civiliansmod.networking.payload.npc.skin.ClientNpcSkinPayload;
import net.asian.civiliansmod.networking.payload.npc.skin.SyncSkinPayload;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.ModCompat;
import net.asian.civiliansmod.util.SkinIdentifier;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
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

import java.util.*;
import java.nio.ByteBuffer;

import static net.asian.civiliansmod.CiviliansMod.*;


public class NPCEntity extends PathAwareEntity {

    private static final TrackedData<Boolean> IS_PAUSED = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> IS_FOLLOWING = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> IS_BATTLE_BUDDY = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Optional<UUID>> OWNER_UUID = DataTracker.registerData(NPCEntity.class, CiviliansMod.OPTIONAL_UUID);
    private static final TrackedData<Float> WANDER_RADIUS = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<BlockPos> WANDER_ANCHOR = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<String> TRADE_PRESET = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> DIALOGUE_ORDERED = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> DIALOGUE_INDEX = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TRACKED_SKIN_VARIANT = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private float originalMaxHealth = 20.0f;
    // Combat system
    public enum CombatState { IDLE, ALERT, ATTACK }
    // Track current combat state
    private CombatState combatState = CombatState.IDLE;
    private final Map<LivingEntity, Integer> ownerHitCount = new HashMap<>();
    private final Map<LivingEntity, Integer> ownerHitExpire = new HashMap<>();
    // Used to remember when NPC was damaged
    private int lastDamagedTick = -200;
    // Used to detect owner damage (alert state)
    private int lastOwnerHurtTick = -200;
    private boolean recentlyDamaged = false;
    private int recentlyDamagedTicks = 0;


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

            CiviliansMod.LOGGER.info(
                    "[CTOR] npc={} age={} trackedVariant={}",
                    this.getUuid(),
                    this.age,
                    this.getTrackedSkinVariant()
            );

            this.setCustomNameVisible(true);
            this.sent = new HashSet<>();

            // Assign a deterministic default skin once (never in replay contexts).
            if (ModCompat.isRealServerWorld(world)
                    && !ModCompat.isInReplay()
                    && this.age == 0
                    && this.dataTracker.get(TRACKED_SKIN_VARIANT) < 0) {

                // Keep legacy behaviour: 0..87 with slim threshold at 44.
                long seed = this.getUuid().getLeastSignificantBits() ^ this.getUuid().getMostSignificantBits();
                Random seededRandom = Random.create(seed);

                int max = 88;

                int variant = seededRandom.nextInt(max);

                this.dataTracker.set(TRACKED_SKIN_VARIANT, variant);

                // only info
                this.skinManager.setBaseVariant(variant);

                // Deterministic name (avoids replay re-randomization).
                this.nameManager.setDeterministicName(this.skinManager.isSlimModel(), seed);
            }
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
        builder.add(WANDER_RADIUS, 16.0f);
        builder.add(TRADE_PRESET, "none");
        builder.add(OWNER_UUID, Optional.empty());
        builder.add(WANDER_ANCHOR, this.getBlockPos());
        builder.add(DIALOGUE_ORDERED, false);
        builder.add(DIALOGUE_INDEX, 0);
        builder.add(TRACKED_SKIN_VARIANT, -1);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);

        if (data == TRACKED_SKIN_VARIANT && this.getWorld().isClient && !ModCompat.isInReplay()) {

            if (this.skinManager.getSkinByteArray() != null) {
                return;
            }

            int variant = this.dataTracker.get(TRACKED_SKIN_VARIANT);
            if (variant >= 0) {
                NPCUtil.ensureSkinsLoaded();
                SkinIdentifier skin = NPCUtil.getNPCTexture(variant);
                if (skin != null) {
                    this.skinManager.setIdSkin(skin);
                    this.refreshSkinModel();
                }
            }
        }
    }

    public void setTrackedSkinVariant(int variant) {
        this.dataTracker.set(TRACKED_SKIN_VARIANT, variant);
    }

    public int getTrackedSkinVariant() {
        return this.dataTracker.get(TRACKED_SKIN_VARIANT);
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
    public void tick() {
        super.tick();

        this.tickHandSwing();

        // Combat state tick
        this.tickCombat();
    }

    //adding COMBAT SYSTEM!!!
    private void tickCombat() {

        // if owner hits only once and no more attacks in 5 second, forget...
        ownerHitExpire.entrySet().removeIf(e -> this.age > e.getValue());
        ownerHitCount.entrySet().removeIf(e -> !ownerHitExpire.containsKey(e.getKey()));

        // if no owner or not battle buddy → skip combat
        if (!this.isBattleBuddy()) {
            this.combatState = CombatState.IDLE;
            return;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null) {
            this.combatState = CombatState.IDLE;
            return;
        }

        LivingEntity currentTarget = this.getTarget();

        // if the npc has an target, attack state
        if (currentTarget != null && currentTarget.isAlive()) {
            this.combatState = CombatState.ATTACK;
            return;
        }

        // if npc was hurt, alert state
        if (this.age - lastDamagedTick < 100) {
            this.combatState = CombatState.ALERT;
            return;
        }

        // if owner was hurt, alert in 5 seconds again, ATTACK?
        if (owner.getAttacker() != null && owner.getAttacker().isAlive()) {
            LivingEntity ownerAttacker = owner.getAttacker();

            // if mob attacks owner, ATTACK
            if (!(ownerAttacker instanceof PlayerEntity)) {
                this.setTarget(ownerAttacker);
                this.combatState = CombatState.ATTACK;
                return;
            }

            // IF PLAYER ATTACKS OWNER,
            // get 2 hit target from AttackEntityCallback
            this.combatState = CombatState.ALERT;
            return;
        }

        // idle if not alert
        this.combatState = CombatState.IDLE;
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        //avoid drowning
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new NPCLeaveWaterGoal(this, 1.2D));
        this.goalSelector.add(2, new NPCAttackGoal(this, 1.2D, true));
        this.goalSelector.add(3, new NPCFollowOwnerGoal(this, 1.0, 10.0f, 2.0f));
        this.goalSelector.add(4, new CustomDoorGoal(this));
        this.goalSelector.add(5, new NPCWanderGoal(this, 0.7));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(7, new LookAroundGoal(this));
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
        // saveSKIN!!!
        writeView.putInt("SkinVariant", this.dataTracker.get(TRACKED_SKIN_VARIANT));
        this.skinManager.writeView(writeView);
    }

    @Override
    protected void readCustomData(ReadView readView) {
        super.readCustomData(readView);

        int variant = readView.getInt("SkinVariant", -1);
        if (variant >= 0) {
            this.setTrackedSkinVariant(variant);
        }
        this.skinManager.readView(readView);
        if (this.getWorld().isClient) {
            SkinManager skinmanager = this.getSkinManager();

            if (skinmanager.getSkinByteArray() != null) {
                skinmanager.uploadDynamicTexture();
                this.refreshSkinModel();

                CiviliansMod.LOGGER.info(
                        "[REPLAY-FIX] Uploaded custom skin from NBT uuid={} bytes={}",
                        this.getUuid(),
                        skinmanager.getSkinByteArray().length
                );
            }
        }
        CiviliansMod.LOGGER.info(
                "[NPC/readCustomData] uuid={} tracked={} skinId={}",
                this.getUuid(),
                this.getTrackedSkinVariant(),
                this.skinManager.getIdSkin()
        );
        this.setPaused(readView.getBoolean("IsPaused", false));
        this.dataTracker.set(IS_FOLLOWING, readView.getBoolean("IsFollowing", false));
        readView.read("Owner", Uuids.CODEC).ifPresent(this::setOwnerUuid);
        this.dataTracker.set(IS_BATTLE_BUDDY, readView.getBoolean("IsBattleBuddy", false));
        this.setWanderRadius(readView.getFloat("WanderRadius", 16.0f));
        this.setWanderAnchor(readView.read("WanderAnchor", BlockPos.CODEC).orElse(this.getBlockPos()));
        this.setTradePreset(readView.getString("TradePreset", "none"));
        this.setDialogueOrdered(readView.getBoolean("DialogueOrdered", false));
        this.setDialogueIndex(readView.getInt("DialogueIndex", 0));
        CiviliansMod.LOGGER.info(
                "[NPC-LOAD] uuid={} trackedVariant={} baseVariant={}",
                this.getUuid(),
                this.getTrackedSkinVariant(),
                this.skinManager.getBaseVariant()
        );
    }

    public boolean isPaused() { return this.dataTracker.get(IS_PAUSED); }
    public void setPaused(boolean paused) {
        this.dataTracker.set(IS_PAUSED, paused);

        if (paused) {
            // stop following
            this.setFollowing(false, null);

            // stop battle buddy
            this.setBattleBuddy(false, null);

            // remove owner
            this.setOwnerUuid(null);

            // set anchor to current position
            this.setWanderAnchor(this.getBlockPos());
        }
    }
    public boolean isFollowing() { return this.dataTracker.get(IS_FOLLOWING); }

    public void setFollowing(boolean following, @Nullable PlayerEntity owner) {
        if (this.isPaused() && following) {
            return;
        }

        this.dataTracker.set(IS_FOLLOWING, following);

        if (following && owner != null) {
            this.setOwner(owner);
        }
    }
    public boolean isBattleBuddy() { return this.dataTracker.get(IS_BATTLE_BUDDY); }
    public void setBattleBuddy(boolean battleBuddy, @Nullable PlayerEntity owner) {
        if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY) {
            CiviliansMod.LOGGER.info("[NPC {}] setBattleBuddy({}, owner={})",
                    this.getId(), battleBuddy, owner != null ? owner.getName().getString() : "null");
        }
        this.dataTracker.set(IS_BATTLE_BUDDY, battleBuddy);

        if (battleBuddy) {
            PlayerEntity actualOwner = owner;
            if (actualOwner == null) {
                LivingEntity o = getOwner();
                if (o instanceof PlayerEntity p) actualOwner = p;
            }

            if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY) {
                CiviliansMod.LOGGER.info("[NPC {}] actualOwner = {}",
                        this.getId(),
                        actualOwner != null ? actualOwner.getName().getString() : "NULL !!!");
            }

            if (actualOwner != null) {
                this.setOwner(actualOwner);
            }

            //battlebudy needs follow
            if (!this.isFollowing()) {
                if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY) {
                    CiviliansMod.LOGGER.info("[NPC {}] Following was OFF → enabling follow", this.getId());
                }
                this.setFollowing(true, actualOwner);
            }
            if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY) {
                CiviliansMod.LOGGER.info("[NPC {}] -> Calling updateBattleBuddyWeapon()", this.getId());
            }
            updateBattleBuddyWeapon();
        } else {
            //battlebuddy off, deactivate weapon
            if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY) {
                CiviliansMod.LOGGER.info("[NPC {}] BattleBuddy OFF – removing weapon", this.getId());
            }
            this.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            //if follow also disabled, delete owner
            if (!this.isFollowing()) {
                if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY) {
                    CiviliansMod.LOGGER.info("[NPC {}] Not following → removing owner", this.getId());
                }
                this.setOwnerUuid(null);
            }
        }

        // health for battle buddy
        if (!this.getWorld().isClient) {
            var healthAttr = this.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (healthAttr != null) {
                if (battleBuddy) {
                    this.originalMaxHealth = this.getMaxHealth();
                    healthAttr.setBaseValue(40.0D);
                    this.heal(40.0F);
                } else {
                    healthAttr.setBaseValue(this.originalMaxHealth);
                    if (this.getHealth() > this.originalMaxHealth) {
                        this.setHealth(this.originalMaxHealth);
                    }
                }
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

    public void updateBattleBuddyWeapon() {
        if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY || DEBUG_AI_BATTLEBUDDY_WEAPON) {
            CiviliansMod.LOGGER.info("[NPC {}] updateBattleBuddyWeapon() called", this.getId());
        }
        if (!isBattleBuddy()) {
            if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY || DEBUG_AI_BATTLEBUDDY_WEAPON) {
                CiviliansMod.LOGGER.warn("[NPC {}] CANCELED → isBattleBuddy=false", this.getId());
            }
            return;
        }

        LivingEntity o = getOwner();
        if (!(o instanceof PlayerEntity owner)) {
            if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY || DEBUG_AI_BATTLEBUDDY_WEAPON) {
                CiviliansMod.LOGGER.warn("[NPC {}] CANCELED → owner is null or NOT PlayerEntity", this.getId());
            }
            return;
        }

        int level = owner.experienceLevel;
        Item target = switchWeaponForLevel(level);

        if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY || DEBUG_AI_BATTLEBUDDY_WEAPON) {
            CiviliansMod.LOGGER.info("[NPC {}] owner = {}", this.getId(), owner.getName().getString());
            CiviliansMod.LOGGER.info("[NPC {}] owner level = {}", this.getId(), owner.experienceLevel);
        }

        if (target == null) {
            if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY || DEBUG_AI_BATTLEBUDDY_WEAPON) {
                CiviliansMod.LOGGER.error("[NPC {}] ERROR → target weapon is NULL!", this.getId());
            }
            return;
        }

        ItemStack current = getMainHandStack();
        if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY || DEBUG_AI_BATTLEBUDDY_WEAPON) {
            CiviliansMod.LOGGER.info("[NPC {}] current weapon = {}", this.getId(), current);
        }

        if (!current.isOf(target)) {
            if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY || DEBUG_AI_BATTLEBUDDY_WEAPON) {
                CiviliansMod.LOGGER.info("[NPC {}] Weapon mismatch → UPGRADING!", this.getId());
            }
            ItemStack newWeapon = new ItemStack(target);
            setStackInHand(Hand.MAIN_HAND, newWeapon);
            equipStack(EquipmentSlot.MAINHAND, newWeapon.copy());
        } else if (DEBUG_AI || DEBUG_AI_BATTLEBUDDY || DEBUG_AI_BATTLEBUDDY_WEAPON) {
            CiviliansMod.LOGGER.info("[NPC {}] Weapon already correct → no change", this.getId());
        }
    }

    public void setOwner(PlayerEntity player) { this.setOwnerUuid(player.getUuid()); }
    //give BattleBuddy weapon from experienceLevel of the owner
    private Item switchWeaponForLevel(int lvl) {
        if (lvl < 5) return Items.WOODEN_SHOVEL;
        else if (lvl < 10) return Items.WOODEN_SWORD;
        else if (lvl < 20) return Items.STONE_SWORD;
        else if (lvl < 30) return Items.IRON_SWORD;
        else if (lvl < 75) return Items.DIAMOND_SWORD;
        else return Items.NETHERITE_SWORD;
    }
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

        // Wenn Battle Buddy aktiv und Owner existiert
        if (this.isBattleBuddy() && this.getOwner() != null) {
            // nur Owner darf interagieren
            if (!player.getUuid().equals(this.getOwner().getUuid())) {
                player.sendMessage(Text.literal("§cThe owner of this NPC is  "
                        + this.getOwner().getName().getString()), true);
                return ActionResult.FAIL;
            }
        }
        if (this.getWorld().isClient) {
            if (hand == Hand.MAIN_HAND && player.isSneaking()) {
                if (this.dialoguesReceived) {
                    this.openCustomNPCScreen();
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        }
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
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
        this.migrateLegacySkinIfNeeded();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        SkinManager skinManager = this.getSkinManager();

        if (skinManager.getSkinByteArray() != null) {
            client.setScreen(new CustomNPCScreen(this));
            return;
        }

        SkinIdentifier skinId = this.getSkinManager().getIdSkin();

        if (skinId == null) {
            // No skin data available (e.g. replay context). Do not attempt to open a skin-specific screen.
            return;
        }

        if (skinId.custom()) {
              client.setScreen(new CustomNPCScreen(this));
        } else if (skinId.slim()) {
            client.setScreen(new SlimNPCScreen(this));
        } else {
            client.setScreen(new DefaultNPCScreen(this));
        }
    }

    @Override
    public Vec3d getLeashOffset() { return new Vec3d(0.0, 0.9, 0.0); }

    public boolean canBeLeashedBy(PlayerEntity player) { return !this.isLeashed() && !this.isBattleBuddy(); }
    public boolean hasSentTo(UUID playerId) { return sent != null && sent.contains(playerId); }
    public void markSentTo(UUID playerId) { if (sent != null) { sent.add(playerId); } }

    @Environment(EnvType.CLIENT)
    public void refreshSkinModel() {

        if (!this.skinManager.isDefaultSkin()) {
            this.calculateDimensions();
            if (DEBUG_TEXTURE) {
                CiviliansMod.LOGGER.info("[Client] RefreshSkinModel(): custom skin – entity invalidated");
            }
            return;
        }

        this.calculateDimensions();
        this.setPosition(this.getX(), this.getY(), this.getZ());

        MinecraftClient.getInstance().worldRenderer.reload();
        if (DEBUG_TEXTURE) {
            CiviliansMod.LOGGER.info("[Client] RefreshSkinModel(): default skin – renderer reloaded.");
        }
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        if (this.getWorld().isClient) {
            SkinIdentifier skinIdentifier = NPCUtil.waitingSync.remove(this.getId());
            if (skinIdentifier != null) {
                this.skinManager.setIdSkin(skinIdentifier);
                if (skinIdentifier.custom()) {
                    this.skinManager.setDefaultSkin(false);
                }
            }
            updateDialoguesTicks = 10;
        }
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        boolean result = super.damage(world, source, amount);

        if (result) {
            // NPC was hurt, used for ALERT combat state
            lastDamagedTick = this.age;
        }

        return result;
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
        // Flashback (replay) must not receive network sync packets.
        if (ModCompat.isInReplay()) {
            return super.createSpawnPacket(entityTrackerEntry);
        }
        CiviliansMod.LOGGER.info(
                "[SpawnCheck] npc={} hasBytes={} variant={}",
                this.getId(),
                this.skinManager.getSkinByteArray() != null,
                this.getTrackedSkinVariant()
        );
        if (!this.getWorld().isClient) {
            if (this.skinManager.getSkinByteArray() == null && this.getTrackedSkinVariant() < 0 && this.skinManager.getBaseVariant() < 0) {
                return super.createSpawnPacket(entityTrackerEntry);
            }

            // Custom Skin
            if (this.skinManager.getSkinByteArray() != null) {
                for (ServerPlayerEntity player : this.getWorld().getServer().getPlayerManager().getPlayerList()) {
                    CiviliansMod.LOGGER.info("[Server] Broadcasting CUSTOM skin npc={} bytes={}", this.getUuid(), this.skinManager.getSkinByteArray() == null ? -1 : this.skinManager.getSkinByteArray().length);
                    ServerPlayNetworking.send(player, new ClientNpcSkinPayload(this.getId(), this.skinManager.isSlimModel(), this.skinManager.getSkinByteArray()));
                }
            } else {
                int variant = this.getTrackedSkinVariant();

                if (variant < 0) {
                    variant = this.skinManager.getBaseVariant(); // LEGACY READ ONLY
                }

                if (variant >= 0) {
                    for (ServerPlayerEntity player : this.getWorld().getServer().getPlayerManager().getPlayerList()) {
                        CiviliansMod.LOGGER.info("[Server] Broadcasting BASE skin npc={} variant={}", this.getUuid(), variant);
                        ServerPlayNetworking.send(player, new SyncSkinPayload(this.getId(), variant));
                    }
                }
            }
        }
        return super.createSpawnPacket(entityTrackerEntry);
    }

    // call if owner was attacked...
    public void onOwnerHit(LivingEntity target) {
        if (target == this || target == this.getOwner()) {
            return;
        }

        int newCount = ownerHitCount.getOrDefault(target, 0) + 1;
        ownerHitCount.put(target, newCount);

        // expiry in 5 seconds
        ownerHitExpire.put(target, this.age + 100);

        // when hit twice, ATTACK
        if (newCount >= 2) {
            this.setTarget(target);
        }
    }

    // call if OWNER was attacked BY A PLAYER (2-hit logic)
    public void onOwnerAttackedByPlayer(LivingEntity attacker) {

        int newCount = ownerHitCount.getOrDefault(attacker, 0) + 1;
        ownerHitCount.put(attacker, newCount);

        // expiry in 5 seconds
        ownerHitExpire.put(attacker, this.age + 100);

        // when hit twice, ATTACK the player
        if (newCount >= 2) {
            this.setTarget(attacker);
        }
    }

    public void migrateLegacySkinIfNeeded() {
        if (ModCompat.isInReplay()) return;
        if (this.getTrackedSkinVariant() >= 0) return;

        int legacy = this.skinManager.getBaseVariant();
        if (legacy < 0) return;

        this.setTrackedSkinVariant(legacy);
    }
}