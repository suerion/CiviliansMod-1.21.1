package net.asian.civiliansmod.entity;

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
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.registry.tag.ItemTags;
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
    private static final TrackedData<Optional<UUID>> OWNER_UUID = DataTracker.registerData(NPCEntity.class, CiviliansMod.OPTIONAL_UUID);
    private static final TrackedData<Float> WANDER_RADIUS = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<BlockPos> WANDER_ANCHOR = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<String> TRADE_PRESET = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> DIALOGUE_ORDERED = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> DIALOGUE_INDEX = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private float originalMaxHealth = 20.0f;
    private int weaponUpdateCooldown = 0;
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
        builder.add(WANDER_RADIUS, 16.0f);
        builder.add(TRADE_PRESET, "none");
        builder.add(OWNER_UUID, Optional.empty());
        builder.add(WANDER_ANCHOR, this.getBlockPos());
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
        this.goalSelector.add(4, new NPCWanderGoal(this, 0.7));
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
        // saveSKIN!!!
        this.skinManager.writeView(writeView);
    }

    @Override
    protected void readCustomData(ReadView readView) {
        super.readCustomData(readView);

        // loadSKIN
        this.skinManager.readNbt(readView);
        this.setPaused(readView.getBoolean("IsPaused", false));
        this.dataTracker.set(IS_FOLLOWING, readView.getBoolean("IsFollowing", false));
        readView.read("Owner", Uuids.CODEC).ifPresent(this::setOwnerUuid);
        this.dataTracker.set(IS_BATTLE_BUDDY, readView.getBoolean("IsBattleBuddy", false));
        this.setWanderRadius(readView.getFloat("WanderRadius", 16.0f));
        this.setWanderAnchor(readView.read("WanderAnchor", BlockPos.CODEC).orElse(this.getBlockPos()));
        this.setTradePreset(readView.getString("TradePreset", "none"));
        this.setDialogueOrdered(readView.getBoolean("DialogueOrdered", false));
        this.setDialogueIndex(readView.getInt("DialogueIndex", 0));
    }

    //tick for checking what level has owner to get better battle buddy :)
    @Override
    public void tick() {
        super.tick();

        // only server side
        if (this.getWorld().isClient) return;

        // Only check if NPC is battle buddy
        if (!this.isBattleBuddy()) return;

        // NPC must have owner
        LivingEntity owner = this.getOwner();
        if (!(owner instanceof PlayerEntity player)) return;

        // cooldown before checking level again
        if (weaponUpdateCooldown-- > 0) return;
        weaponUpdateCooldown = 40; // update every 2 seconds

        // get weapon from owner lvl
        ItemStack newWeapon = weaponFromOwner(player);
        ItemStack current = this.getMainHandStack();

        // only upgrade
        if (!ItemStack.areItemsEqual(current, newWeapon)) {
            this.equipStack(EquipmentSlot.MAINHAND, newWeapon);
        }
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
        this.dataTracker.set(IS_BATTLE_BUDDY, battleBuddy);

        if (battleBuddy) {
            // give owner to battlebuddy
            if (!this.isPaused() && this.getOwnerUuid().isEmpty() && owner != null) {
                this.setOwner(owner);
            }

            //give battlebuddy weapon from experience level of the owner
            if (!this.isPaused() && owner != null) {
                ItemStack weapon = weaponFromOwner(owner);
                this.equipStack(EquipmentSlot.MAINHAND, weapon);
            }

        } else {
            // delete owner and weapon if follow not activated
            if (!this.isPaused() && !this.isFollowing()) {
                this.setOwnerUuid(null);
                this.setWanderAnchor(this.getBlockPos());
            }
            this.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
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
    public void setOwner(PlayerEntity player) { this.setOwnerUuid(player.getUuid()); }
    //give BattleBuddy weapon from experienceLevel of the owner
    private ItemStack weaponFromOwner(PlayerEntity owner) {
        int lvl = owner.experienceLevel;

        if (lvl < 5) {
            return new ItemStack(Items.WOODEN_SHOVEL);
        } else if (lvl < 10) {
            return new ItemStack(Items.WOODEN_SWORD);
        } else if (lvl < 15) {
            return new ItemStack(Items.GOLDEN_SWORD);
        } else if (lvl < 25) {
            return new ItemStack(Items.STONE_SWORD);
        } else if (lvl < 40) {
            return new ItemStack(Items.IRON_SWORD);
        } else if (lvl < 75) {
            return new ItemStack(Items.DIAMOND_SWORD);
        } else {
            return new ItemStack(Items.NETHERITE_SWORD);
        }
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        SkinManager skinManager = this.getSkinManager();

        if (!skinManager.isDefaultSkin() && skinManager.getSkinByteArray() != null && skinManager.getIdSkin() != null) {
            client.setScreen(new CustomNPCScreen(this));
            return;
        }

        int baseVariant = this.getSkinManager().getBaseVariant();
        SkinIdentifier skinId = NPCUtil.getNPCTexture(baseVariant);

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
        this.calculateDimensions();
        this.setPosition(this.getX(), this.getY(), this.getZ());
        MinecraftClient.getInstance().worldRenderer.reload();
        CiviliansMod.LOGGER.info("[Client] RefreshSkinModel() executed for NPC {}", this.getId());
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        if (this.getWorld().isClient) {
            SkinIdentifier skinIdentifier = NPCUtil.waitingSync.get(this.getId());
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