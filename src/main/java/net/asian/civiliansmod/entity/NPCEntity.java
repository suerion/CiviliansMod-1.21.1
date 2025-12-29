package net.asian.civiliansmod.entity;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.chat.DefaultChat;
import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.goal.CustomDoorGoal;
import net.asian.civiliansmod.gui.CustomNPCScreen;
import net.asian.civiliansmod.gui.DefaultNPCScreen;
import net.asian.civiliansmod.gui.SlimNPCScreen;
import java.util.Arrays;
import net.asian.civiliansmod.networking.payload.npc.dialogue.ClientDialogueSyncPayload;
import net.asian.civiliansmod.networking.payload.npc.dialogue.DialogueSyncPayload;
import net.asian.civiliansmod.networking.payload.npc.dialogue.OpenScreenDialoguesPayload;
import net.asian.civiliansmod.networking.payload.npc.skin.ClientNpcSkinPayload;
import net.asian.civiliansmod.networking.payload.npc.skin.SyncSkinPayload;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.SkinIdentifier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;

public class NPCEntity extends PathAwareEntity {
    private float targetYaw = 0.0F; // The yaw to smoothly rotate towards
    private boolean isTurning = false; // Whether the NPC is currently in the process of turning
    private int lookAtPlayerTicks = 0;
    private static final TrackedData<Boolean> IS_PAUSED = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private int regenerationCooldown = 0;
    private static final TrackedData<Boolean> IS_FOLLOWING = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> TRACKED_SKIN_VARIANT = DataTracker.registerData(NPCEntity.class, TrackedDataHandlerRegistry.INTEGER);
    int updateDialoguesTicks = 0;

    //@Environment(EnvType.SERVER)
    Set<UUID> sent = new HashSet<>();

    @Environment(EnvType.CLIENT)
    public boolean dialoguesReceived;

    public NameManager getNameManager() {
        return nameManager;
    }
    public SkinManager getSkinManager() {
        return skinManager;
    }

    public boolean isLegacyNpc() {
        return this.skinManager.skinIdentifier == null
                && this.skinManager.baseVariant >= 0
                && this.getDataTracker().get(TRACKED_SKIN_VARIANT) < 0;
    }

    public ChatManager getChatManager() { return chatManager; }

    public static TrackedData<Integer> getTrackedSkinVariant() {
        return TRACKED_SKIN_VARIANT;
    }

    NameManager nameManager = new NameManager(this);
    SkinManager skinManager = new SkinManager(this);
    ChatManager chatManager = new ChatManager(this);

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);

        if (this.isLegacyNpc()) {
            return;
        }

        if (!this.getWorld().isClient || !FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)) {
            return;
        }

        if (NPCUtil.getSkins().isEmpty()) {
            CiviliansMod.LOGGER.warn(
                    "[NPC/SPAWN] id={} skins not ready yet, delaying skin apply",
                    this.getId()
            );
            NPCUtil.ensureSkinsLoaded();
        }

        int tracked = this.getDataTracker().get(TRACKED_SKIN_VARIANT);
        if (tracked >= 0) {
            SkinIdentifier skin = NPCUtil.getNPCTexture(tracked);
            if (skin != null) {
                this.skinManager.applySkin(skin);
            }
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
        if (this.getWorld().isClient) {
            return super.createSpawnPacket(entityTrackerEntry);
        }
        if (!this.skinManager.skinSynced) {

            int tracked = this.getDataTracker().get(TRACKED_SKIN_VARIANT);

            if (tracked < 0 && this.skinManager.skinIdentifier == null) {
                CiviliansMod.LOGGER.warn(
                        "[NPC/SPAWN] id={} delaying skin sync (tracked={}, baseVariant={})",
                        this.getId(),
                        tracked,
                        this.skinManager.baseVariant
                );
                return super.createSpawnPacket(entityTrackerEntry);
            }

            SkinIdentifier skin = this.skinManager.getIdSkin();
            if (skin == null && this.skinManager.skinByteArray == null) {
                return super.createSpawnPacket(entityTrackerEntry);
            }

            if (this.skinManager.skinByteArray == null) {
                for (ServerPlayerEntity player : Objects.requireNonNull(this.getWorld().getServer()).getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(player, new SyncSkinPayload(this.getId(), skin.id(), skin.slim()));
                }
                this.skinManager.skinSynced = true;
            } else {
                for (ServerPlayerEntity player : Objects.requireNonNull(this.getWorld().getServer()).getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(player, new ClientNpcSkinPayload(this.getId(), this.skinManager.slim, this.skinManager.skinByteArray));
                }
                this.skinManager.skinSynced = true;
            }
        }
        return super.createSpawnPacket(entityTrackerEntry);
    }

    public NPCEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
/*
        CiviliansMod.LOGGER.info(
                "[NPC/CTOR:PRE_INIT] id={} worldClient={} flashback={} baseVariant={} skinId={}",
                this.getId(),
                world.isClient,
                CiviliansMod.isFlashbackReplay(),
                this.skinManager.baseVariant,
                this.skinManager.skinIdentifier
        );
*/
        if (this.getCustomName() == null) {
            this.nameManager.setRandomName(this.skinManager.slim);
        }
/*
        CiviliansMod.LOGGER.info(
                "[NPC/CTOR:FINAL_STATE] id={} baseVariant={} skinId={}",
                this.getId(),
                this.skinManager.baseVariant,
                this.skinManager.skinIdentifier
        );*/

        if (!world.isClient) {
            this.sent = new HashSet<>();
            this.setCustomNameVisible(true);
        } else {
            this.dialoguesReceived = false;
        }
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        this.chatManager = new ChatManager(this);
        super.initDataTracker(builder);
        builder.add(IS_PAUSED, false);
        builder.add(IS_FOLLOWING, false);
        builder.add(TRACKED_SKIN_VARIANT, -1);
/*
        CiviliansMod.LOGGER.info(
                "[NPC/DATATRACKER/INIT] id={} initial trackedVariant={}",
                this.getId(),
                -1
        );*/

    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    public boolean isFollowing() {
        return this.dataTracker.get(IS_FOLLOWING);
    }

    public void setFollowing(boolean following) {
        this.dataTracker.set(IS_FOLLOWING, following);
    }

    public boolean isPaused() {
        return this.dataTracker.get(IS_PAUSED);
    }

    public void setPaused(boolean paused) {
        this.dataTracker.set(IS_PAUSED, paused);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        // Save the variant to NBT
        nbt.putBoolean("IsPaused", this.isPaused());
        nbt.putBoolean("IsFollowing", this.isFollowing());
        nbt.put("dialogues", chatManager.saveDialogues());
        this.skinManager.writeNbt(nbt);

        CiviliansMod.LOGGER.info(
                "[NPC/SAVE] Writing NPC {} to NBT in chunk {} {}",
                this.getId(),
                this.getBlockPos().getX() >> 4,
                this.getBlockPos().getZ() >> 4
        );
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        this.skinManager.readNbt(nbt);

// 1️⃣ baseVariant IMMER aus NBT lesen
        int nbtVariant = nbt.getInt("basevariant").orElse(-1);
        if (nbtVariant >= 0) {
            this.skinManager.baseVariant = nbtVariant;
        }

// 2️⃣ Tracker hat Priorität
        if (this.getDataTracker().get(TRACKED_SKIN_VARIANT) < 0 && this.skinManager.baseVariant >= 0) {
            this.getDataTracker().set(TRACKED_SKIN_VARIANT, this.skinManager.baseVariant);
        }

// 3️⃣ Logging
        CiviliansMod.LOGGER.info(
                "[NPC/READ/NBT] id={} baseVariant={} tracked={} hasSkinId={}",
                this.getId(),
                this.skinManager.baseVariant,
                this.getDataTracker().get(TRACKED_SKIN_VARIANT),
                this.skinManager.skinIdentifier != null
        );

        if (nbt.contains("IsPaused")) {
            this.setPaused(nbt.getBoolean("IsPaused").orElse(false));
        }
        if (nbt.contains("IsFollowing")) {
            this.setFollowing(nbt.getBoolean("IsFollowing").orElse(false));
        }
        if (nbt.contains("dialogues")) {
            this.chatManager.setFromNbt(nbt.getCompound("dialogues"));
        }

        this.skinManager.skinSynced = false;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0) // 20HP
                .add(EntityAttributes.MOVEMENT_SPEED, 0.3); // Adjusted speed
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.add(1, new WanderAroundFarGoal(this, 0.7));
        this.goalSelector.add(6, new CustomDoorGoal(this));
        this.goalSelector.add(4, new LookAroundGoal(this));
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        // Check if the entity is in a "paused" state (custom logic)
        if (isPaused()) {
            return false; // Prevent damage and stop further processing
        }

        // Call the new `super.damage` method with the correct parameters
        boolean hurt = super.damage(world, source, amount);

        // If the entity was damaged and there is an attacker
        if (hurt && source.getAttacker() != null) {
            if (!world.isClient()) {
                Text nameText = this.getCustomName();
                String npcName = (nameText != null) ? nameText.getString() : "NPC";


                if (source.getAttacker() instanceof PlayerEntity player) {
                    String hitDialogue = chatManager.getRandomChat(CiviliansMod.playerLanguages.get(player.getUuid()), NpcChat.ChatReason.HURT);
                    player.sendMessage(Text.literal(npcName + ": " + hitDialogue), true);

                }

                // Define flee behavior: Calculate direction vector for fleeing
                double dx = this.getX() - source.getAttacker().getX();
                double dz = this.getZ() - source.getAttacker().getZ();
                double fleeDistance = 12.0;

                // Start moving the entity away from the attacker
                this.getNavigation().startMovingTo(
                        this.getX() + dx * fleeDistance,
                        this.getY(),
                        this.getZ() + dz * fleeDistance,
                        1.2 // Movement speed when fleeing
                );
            }
        }

        return hurt; // Return whether the entity was successfully damaged
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        CiviliansMod.LOGGER.info("[CiviliansMod] This is the NPC: {}", this.getId());

        // Ensure the interaction is in the main hand
        if (hand == Hand.MAIN_HAND) {
            // Check if the player is holding a lead
            ItemStack heldItem = player.getStackInHand(hand);
            if (heldItem.isOf(Items.LEAD) && !this.hasPassengers()) {
                // Leash the NPC to the player if not already leashed
                if (!this.getWorld().isClient()) {
                    if (this.canBeLeashedBy(player)) {
                        this.attachLeash(player, true);
                        return ActionResult.SUCCESS;
                    }
                }
            }

            // Check if the player is sneaking
            if (player.isSneaking()) {
                if (!this.getWorld().isClient()) {
                    // SERVER-SIDE: Handle dialogue payload and NPC behavior
                    this.getNavigation().stop();

                    double dx = player.getX() - this.getX();
                    double dz = player.getZ() - this.getZ();
                    targetYaw = (float) (Math.atan2(dz, dx) * (180F / Math.PI)) - 90F;
                    isTurning = true;
                    this.lookAtPlayerTicks = 60;

                    if (player instanceof ServerPlayerEntity serverPlayer) {

                        this.sent.remove(player.getUuid());

                        if (!hasSentTo(player.getUuid())) {
                            markSentTo(player.getUuid());
                            OpenScreenDialoguesPayload payload = new OpenScreenDialoguesPayload(this.getId(), this.chatManager.getDialogues());
                            ServerPlayNetworking.send(serverPlayer, payload);
                        }
                    }

                    return ActionResult.SUCCESS;
                } else {
                    // CLIENT-SIDE: Only handle GUI opening when dialogues are received
                    // Remove the chat message here - it doesn't belong in sneak interaction
                    if (this.dialoguesReceived) {
                        CiviliansMod.LOGGER.info("[CiviliansMod] Opening GUI for NPC {}", this.getId());
                        openCustomNPCScreen();
                    } else {
                        CiviliansMod.LOGGER.warn("[CiviliansMod] No dialogues yet for NPC {}", this.getId());
                    }
                    return ActionResult.SUCCESS;
                }
            } else {
                // NORMAL INTERACTION (not sneaking) - Send chat message
                if (!this.getWorld().isClient()) {
                    this.getNavigation().stop();

                    double dx = player.getX() - this.getX();
                    double dz = player.getZ() - this.getZ();
                    targetYaw = (float) (Math.atan2(dz, dx) * (180F / Math.PI)) - 90F;
                    isTurning = true;
                    this.lookAtPlayerTicks = 60;

                    Text nameText = this.getCustomName();
                    String npcName = nameText != null ? nameText.getString() : "NPC";
                    String dialogue = chatManager.getRandomChat(CiviliansMod.playerLanguages.get(player.getUuid()), NpcChat.ChatReason.INTERACT);
                    player.sendMessage(Text.literal(npcName + ": " + dialogue), true);
                }
                return ActionResult.SUCCESS;
            }
        }
        // Delegate to superclass for other interactions
        return super.interactMob(player, hand);
    }

    @Environment(EnvType.CLIENT)
    public void openCustomNPCScreen() {
        if (skinManager.slim && skinManager.defaultSkin) {
            MinecraftClient.getInstance().setScreen(new SlimNPCScreen(this));
        } else if (skinManager.defaultSkin) {
            MinecraftClient.getInstance().setScreen(new DefaultNPCScreen(this));
        } else {
            MinecraftClient.getInstance().setScreen(new CustomNPCScreen(this));
        }
    }

    @Override
    public Vec3d getLeashOffset() {
        // Adjusted offset to attach the leash to the NPC's hips
        return new Vec3d(0.0, 0.9, 0.0); // Adjust the Y-axis offset as needed.
    }

    public boolean canBeLeashedBy(PlayerEntity player) {
        // Allow leashing only if the player is in survival or adventure mode
        return !this.isLeashed() && !player.isSneaking();
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            if (updateDialoguesTicks > 0 && --updateDialoguesTicks == 0) {
                ClientPlayNetworking.send(new ClientDialogueSyncPayload(this.getUuid()));
            }
        }
    }

    @Override
    public void tickMovement() {
        // Handle paused state
        if (isPaused()) {
            // Ensure NPC does not move while paused
            this.getNavigation().stop();
            this.setVelocity(0.0, 0.0, 0.0);

            // Make the NPC look at the nearest player within 5 blocks
            PlayerEntity nearestPlayer = this.getWorld().getClosestPlayer(this, 5.0);
            if (nearestPlayer != null) {
                // Calculate direction for looking at the player
                double dx = nearestPlayer.getX() - this.getX();
                double dy = nearestPlayer.getEyeY() - this.getEyeY(); // Adjust for eye level
                double dz = nearestPlayer.getZ() - this.getZ();
                double distance = Math.sqrt(dx * dx + dz * dz);

                // Calculate yaw and pitch for the NPC to face the player
                float targetYaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F; // Horizontal rotation
                float targetPitch = (float) -(Math.atan2(dy, distance) * (180.0 / Math.PI)); // Vertical rotation

                // Smoothly adjust the headYaw and pitch towards the target
                this.headYaw = adjustTowards(this.headYaw, targetYaw);
                this.setPitch(adjustTowards(this.getPitch(), targetPitch));
            }
            return; // Skip additional logic while paused
        }

        // Handle "Follow" state
        if (isFollowing() && this.getWorld() != null && !this.getWorld().isClient) {
            PlayerEntity nearestPlayer = this.getWorld().getClosestPlayer(this, 15); // Follow within a 10-block radius

            if (nearestPlayer != null) {
                double distanceToPlayer = this.squaredDistanceTo(nearestPlayer);

                // Check the distance and adjust behavior dynamically
                if (distanceToPlayer > 4.0 && distanceToPlayer < 400.0) { // Follow if distance > 2 blocks but < 20 blocks
                    double deltaX = nearestPlayer.getX() - this.getX();
                    nearestPlayer.getEyeY();
                    this.getEyeY();
                    double deltaZ = nearestPlayer.getZ() - this.getZ();

                    // Dynamically adjust the speed based on distance (faster speed if farther away)
                    double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    double followSpeed = Math.min(3.5, 0.35 + (distance / 10.0)); // Max speed capped at 2.5

                    // Start moving towards the player
                    this.getNavigation().startMovingTo(nearestPlayer, followSpeed);


                    if (distance > 16.0) {
                        this.refreshPositionAndAngles(nearestPlayer.getX() - deltaX / 2, nearestPlayer.getY(), nearestPlayer.getZ() - deltaZ / 2, this.getYaw(), this.getPitch());
                        this.getNavigation().stop(); // Prevent glitches just after teleportation
                    }
                } else if (distanceToPlayer <= 4.0) {
                    this.getNavigation().stop(); // NPC is close enough; stop moving
                }
            } else {
                // Stop moving if no player is nearby
                this.getNavigation().stop();
            }
        }

        // Additional movement logic like smooth turning
        super.tickMovement();

        // Handle smooth turning (called every tick)
        if (isTurning) {
            smoothTurnToTargetYaw();
        }

        // Countdown for "lookAtPlayerTicks" behavior
        if (this.lookAtPlayerTicks > 0) {
            this.lookAtPlayerTicks--; // Decrease look timer
            this.getNavigation().stop(); // Ensure NPC does not move while focusing
            this.setVelocity(0.0, 0.0, 0.0);
        }

        // Handle regeneration logic
        if (this.isAlive() && this.getHealth() < this.getMaxHealth()) {
            if (regenerationCooldown <= 0) {
                this.heal(1.0F); // Regenerate 1 health every cooldown reset
                regenerationCooldown = 20; // Reset cooldown (20 ticks = 1 second)
            } else {
                regenerationCooldown--; // Decrease regeneration cooldown every tick
            }
        }
    }

    // Smoothly adjust the current angle towards a target angle
    private float adjustTowards(float current, float target) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (delta > (float) 5.0) {
            delta = (float) 5.0; // Cap the increase
        } else if (delta < -(float) 5.0) {
            delta = -(float) 5.0; // Cap the decrease
        }
        return current + delta; // Adjust current angle
    }

    private void smoothTurnToTargetYaw() {
        float turnRate = 7.5F; // Amount to rotate per tick (increase for faster turning)
        float yawDifference = wrapDegrees(targetYaw - this.getYaw()); // Calculate the difference to the target yaw

        // Stop turning if we're very close to the target yaw
        if (Math.abs(yawDifference) < 1.0F) {
            this.setYaw(targetYaw); // Snap to the target
            this.bodyYaw = targetYaw;
            this.headYaw = targetYaw;
            isTurning = false; // We’re done turning
        } else {
            // Apply only part of the rotation to smooth out the turn
            float yawAdjustment = Math.min(turnRate, Math.max(-turnRate, yawDifference));
            this.setYaw(this.getYaw() + yawAdjustment);
            this.bodyYaw = this.getYaw();
            this.headYaw = this.getYaw();
        }
    }

    private float wrapDegrees(float degrees) {
        while (degrees >= 180.0F) {
            degrees -= 360.0F;
        }
        while (degrees < -180.0F) {
            degrees += 360.0F;
        }
        return degrees;
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);

        if (data == TRACKED_SKIN_VARIANT && this.getWorld().isClient) {
            int tracked = this.getDataTracker().get(TRACKED_SKIN_VARIANT);

            CiviliansMod.LOGGER.info(
                    "[NPC/TRACKED/SKIN] id={} tracked={}",
                    this.getId(),
                    tracked
            );

            if (tracked >= 0) {
                SkinIdentifier skin = NPCUtil.getNPCTexture(tracked);
                if (skin != null) {
                    this.skinManager.applySkin(skin);
                }
            }
            if (tracked < 0) {
                CiviliansMod.LOGGER.warn(
                        "[NPC/TRACKED/SKIN] id={} invalid trackedVariant",
                        this.getId()
                );
                return;
            }
        }
    }

    public boolean hasSentTo(UUID playerId) {
        return sent.contains(playerId);
    }

    public void markSentTo(UUID playerId) {
        sent.add(playerId);
    }

    public static class ChatManager {
        NPCEntity npc;
        Map<String, Map<NpcChat.ChatReason, List<String>>> dialogues;
        Map<NpcChat.ChatReason, List<String>> customDialogues = new EnumMap<>(NpcChat.ChatReason.class);

        public ChatManager(NPCEntity npc) {
            this.npc = npc;
            this.dialogues = new HashMap<>(DefaultChat.getDefaultChat());
        }

        public String getRandomChat(String language, NpcChat.ChatReason reason) {
            List<String> custom = customDialogues.get(reason);
            if (custom != null && !custom.isEmpty()) {
                return custom.get(Random.create().nextInt(custom.size()));
            }

            Map<NpcChat.ChatReason, List<String>> langDialogues = dialogues.get(language);
            if (langDialogues == null || langDialogues.isEmpty()) {
                //CiviliansMod.LOGGER.warn("[CiviliansMod] No dialogues for language {}, falling back to en_us (NPC ID: {})",language , npc.getId());
                langDialogues = DefaultChat.getDefaultChat().get("en_us");
            }

            if (langDialogues == null && !dialogues.isEmpty()) {
                langDialogues = dialogues.values().iterator().next();
                //CiviliansMod.LOGGER.warn("[CiviliansMod] No en_us dialogues, using first available language for NPC {}", npc.getId());
            }

            if (langDialogues == null) {
                //CiviliansMod.LOGGER.warn("[CiviliansMod] No dialogues available at all, using placeholder for NPC {}", npc.getId());
                langDialogues = new EnumMap<>(NpcChat.ChatReason.class);
            }

            if (dialogues.isEmpty()) {
                //CiviliansMod.LOGGER.error("[CiviliansMod] Dialogue map empty, loading default dialogues for NPC {}", npc.getId());
                dialogues.putAll(DefaultChat.getDefaultChat());
            }

            List<String> messages = langDialogues.getOrDefault(reason, Collections.singletonList("..."));
            if (messages.isEmpty()) {
                messages = Collections.singletonList("...");
            }
            return messages.get(Random.create().nextInt(messages.size()));
        }

        public Map<NpcChat.ChatReason, List<String>> getDialoguesForLanguage(String language) {
            //map for language
            Map<NpcChat.ChatReason, List<String>> languageMap = dialogues.get(language);

            // fallback if no lang
            if (languageMap == null || languageMap.isEmpty()) {
                //CiviliansMod.LOGGER.warn("[CiviliansMod] No dialogues for language {}, falling back to en_us (NPC ID: {})", language, npc.getId());
                languageMap = DefaultChat.getDefaultChat().get("en_us");
            }
            //fallback if en_us is not available on error, use first other language
            if (languageMap == null && !dialogues.isEmpty()) {
                languageMap = dialogues.values().iterator().next();
                //CiviliansMod.LOGGER.warn("[CiviliansMod] No en_us dialogues, using first available language for NPC {}", npc.getId());
            }
            //fallback if nothing works to DefaultChat
            if (languageMap == null) {
                //CiviliansMod.LOGGER.warn("[CiviliansMod] No dialogues available at all, creating default placeholder map for NPC {}", npc.getId());
                languageMap = new EnumMap<>(NpcChat.ChatReason.class);
            }
            // get allways a placeholder list for reasons
            for (NpcChat.ChatReason reason : NpcChat.ChatReason.values()) {
                languageMap.computeIfAbsent(reason, r -> new ArrayList<>(Collections.singletonList("...")));
            }
            return languageMap;
        }

        public Map<NpcChat.ChatReason, List<String>> getTranslatedDialogues(String language) {
            return getDialoguesForLanguage(language);
        }

        public NbtCompound saveDialogues() {
            NbtCompound main = new NbtCompound();

            //save language dialogue
            NbtCompound langs = new NbtCompound();
            for (Map.Entry<String, Map<NpcChat.ChatReason, List<String>>> langEntry : dialogues.entrySet()) {
                NbtCompound langCompound = new NbtCompound();
                for (Map.Entry<NpcChat.ChatReason, List<String>> reasonEntry : langEntry.getValue().entrySet()) {
                    NbtList list = new NbtList();
                    for (String msg : reasonEntry.getValue()) {
                        list.add(NbtString.of(msg));
                    }
                    langCompound.put(reasonEntry.getKey().getName(), list);
                }
                langs.put(langEntry.getKey(), langCompound);
            }
            main.put("Languages", langs);

            //save custom dialogue
            NbtCompound customCompound = new NbtCompound();
            for (Map.Entry<NpcChat.ChatReason, List<String>> entry : customDialogues.entrySet()) {
                NbtList list = new NbtList();
                for (String msg : entry.getValue()) {
                    list.add(NbtString.of(msg));
                }
                customCompound.put(entry.getKey().getName(), list);
            }
            main.put("CustomDialogues", customCompound);

            return main;
        }

        public void setFromNbt(Optional<NbtCompound> nbtOptional) {
            if (nbtOptional.isEmpty()) {
                return;
            }

            dialogues.clear();
            customDialogues.clear();

            NbtCompound nbt = nbtOptional.get();
            Optional<NbtCompound> langsOpt = nbt.getCompound("Languages");
            if (langsOpt.isPresent()) {
                NbtCompound langs = langsOpt.get();

                for (String language : langs.getKeys()) {
                    Optional<NbtCompound> langCompoundOpt = langs.getCompound(language);
                    if (langCompoundOpt.isEmpty()) continue;
                    NbtCompound langCompound = langCompoundOpt.get();
                    Map<NpcChat.ChatReason, List<String>> reasonMap = new EnumMap<>(NpcChat.ChatReason.class);

                    for (NpcChat.ChatReason reason : NpcChat.ChatReason.values()) {
                        if (langCompound.contains(reason.getName())) {
                            Optional<NbtList> listOpt = langCompound.getList(reason.getName());
                            if (listOpt.isEmpty()) continue;
                            NbtList list = listOpt.get();
                            List<String> messages = new ArrayList<>();
                            list.forEach(e -> messages.add(e.asString().orElse("")));

                            reasonMap.put(reason, messages);
                        }
                    }

                    dialogues.put(language, reasonMap);
                }
            }


            Optional<NbtCompound> customOpt = nbt.getCompound("CustomDialogues");
            if (customOpt.isPresent()) {
                NbtCompound custom = customOpt.get();


                for (NpcChat.ChatReason reason : NpcChat.ChatReason.values()) {
                    if (custom.contains(reason.getName())) {
                        Optional<NbtList> listOpt = custom.getList(reason.getName());
                        if (listOpt.isEmpty()) continue;

                        NbtList list = listOpt.get();
                        List<String> messages = new ArrayList<>();
                        list.forEach(e -> messages.add(e.asString().orElse("")));
                        customDialogues.put(reason, messages);
                    }
                }
            }

            if (dialogues.isEmpty()) {
                dialogues.putAll(DefaultChat.getDefaultChat());
            }
        }

        public Map<String, Map<NpcChat.ChatReason, List<String>>> getDialogues() {
            return dialogues;
        }

        public void setDialogues(Map<String, Map<NpcChat.ChatReason, List<String>>> newDialogues) {
            dialogues.clear();
            dialogues.putAll(newDialogues);
        }

        public Map<NpcChat.ChatReason, List<String>> getCustomDialogues() {
            return customDialogues;
        }

        public void setCustomDialogues(Map<NpcChat.ChatReason, List<String>> map) {
            customDialogues.clear();
            customDialogues.putAll(map);
        }

        public void markDialoguesDirty(UUID avoid) {
            if (!(npc.getWorld() instanceof ServerWorld serverWorld)) return;

            for (ServerPlayerEntity player : serverWorld.getPlayers(p -> !p.getUuid().equals(avoid))) {
                try {
                    ServerPlayNetworking.send(player, new DialogueSyncPayload(npc.getId(), dialogues, customDialogues));
                } catch (IOException e) {
                    CiviliansMod.LOGGER.error("[CiviliansMod] Failed to sync dialogues to player {}", player.getGameProfile().getName(), e);
                }
            }
        }

        static @NotNull BiConsumer<String, Map<NpcChat.ChatReason, List<String>>> getManageCompoundSave(NbtCompound mainCompound) {
            return (language, reasonToMessagesMap) -> {
                NbtCompound languageCompound = new NbtCompound();

                reasonToMessagesMap.forEach((reason, messages) -> {
                    NbtList messageList = new NbtList();
                    messages.forEach(message -> messageList.add(NbtString.of(message)));

                    // Ajout de la liste des messages pour une raison donnée
                    languageCompound.put(reason.getName(), messageList);
                });

                // Ajout du compound pour la langue
                mainCompound.put(language, languageCompound);
            };
        }
    }

    public static class SkinManager {
        byte[] skinByteArray;

        SkinIdentifier skinIdentifier;

        boolean skinSynced = false;

        public void setBaseVariant(int baseVariant) {
            this.baseVariant = baseVariant;
        }

        int baseVariant;

        boolean slim;

        NPCEntity npcEntity;

        boolean defaultSkin;

        public SkinManager(NPCEntity npcEntity) {
            this.npcEntity = npcEntity;
            this.defaultSkin = true;
            this.baseVariant = -1;
            this.slim = false;
        }

        public boolean isSlim() {
            return slim;
        }

        public void setSkinByteArray(byte[] skinByteArray) {
            this.skinByteArray = skinByteArray;
        }

        @Environment(EnvType.CLIENT)
        public void setIdSkin(SkinIdentifier skin) {
            this.skinIdentifier = skin;
        }

        public boolean hasExplicitSkinId() {
            return this.skinIdentifier != null;
        }

        public SkinIdentifier getIdSkin() {

            // explizit gespeichert → IMMER verwenden
            if (this.skinIdentifier != null) {
                return this.skinIdentifier;
            }

            // Legacy
            if (npcEntity.isLegacyNpc()) {
                if (this.baseVariant >= 0) {
                    return NPCUtil.getNPCTexture(this.baseVariant);
                }
                return null;
            }

            // Neue NPCs
            int tracked = npcEntity.getDataTracker().get(TRACKED_SKIN_VARIANT);
            if (tracked >= 0) {
                return NPCUtil.getNPCTexture(tracked);
            }

            return null;
        }

        void writeNbt(NbtCompound nbt) {
            /*
            CiviliansMod.LOGGER.info(
                    "[NPC/NBT-WRITE] id={} baseVariant={} skinId={} slim={} default={}",
                    npcEntity.getId(),
                    this.baseVariant,
                    this.skinIdentifier,
                    this.slim,
                    this.defaultSkin
            );
             */
            if (this.baseVariant >= 0) {
                nbt.putInt("basevariant", this.baseVariant);
            }

            if (this.skinIdentifier != null) {
                nbt.putString("skin_id", this.skinIdentifier.id().toString());
                nbt.putBoolean("skin_slim", this.skinIdentifier.slim());
                nbt.putBoolean("skin_custom", this.skinIdentifier.custom());
            }

            if (skinByteArray != null) {
                nbt.putByteArray("skin", skinByteArray);
            }
        }

        void readNbt(NbtCompound nbt) {
            /*
            CiviliansMod.LOGGER.info(
                    "[NPC/NBT-READ] id={} hasSkinId={} hasBaseVariant={} hasSkinBytes={}",
                    npcEntity.getId(),
                    nbt.contains("skin_id"),
                    nbt.contains("basevariant"),
                    nbt.contains("skin")
            );
            */
            if (nbt.contains("skin_id")) {
                Identifier id = Identifier.of(nbt.getString("skin_id").orElse(""));
                boolean slim = nbt.getBoolean("skin_slim").orElse(false);
                boolean custom = nbt.getBoolean("skin_custom").orElse(false);

                this.skinIdentifier = new SkinIdentifier(id, slim, custom);
                this.slim = slim;
                this.defaultSkin = false;
            }
            if (this.baseVariant < 0) {
                this.baseVariant = nbt.getInt("basevariant").orElse(-1);
            }

            if (nbt.contains("skin")) {
                byte[] skinData = nbt.getByteArray("skin").orElse(null);
                if (skinData != null && skinData.length > 0) {
                    this.skinByteArray = Arrays.copyOf(skinData, skinData.length);
                    this.defaultSkin = false;
                }
            } else if (this.skinIdentifier == null && this.skinByteArray == null) {
                this.defaultSkin = true;
            }
            /*
            CiviliansMod.LOGGER.info(
                    "[NPC/NBT-READ] RESULT id={} baseVariant={} skinId={} slim={} default={}",
                    npcEntity.getId(),
                    this.baseVariant,
                    this.skinIdentifier,
                    this.slim,
                    this.defaultSkin
            );
             */
        }

        public void setSlim(boolean slim) {
            this.slim = slim;
        }

        public void applySkin(SkinIdentifier skin) {
            this.skinIdentifier = skin;
            this.slim = skin.slim();
            this.defaultSkin = false;
            this.skinSynced = true;
/*
            CiviliansMod.LOGGER.info(
                    "[NPC/SKIN/APPLY] id={} skin={} slim={} custom={} baseVariant={}",
                    npcEntity.getId(),
                    skin.id(),
                    skin.slim(),
                    skin.custom(),
                    baseVariant
            );

 */
            if (this.skinIdentifier != null) {
                CiviliansMod.LOGGER.info(
                        "[NPC/SKIN/FINAL] id={} skin={} tracked={} baseVariant={}",
                        npcEntity.getId(),
                        skin.id(),
                        npcEntity.getDataTracker().get(TRACKED_SKIN_VARIANT),
                        this.baseVariant
                );
            }
        }

        public void setDefaultSkin(boolean defaultSkin) {
            this.defaultSkin = defaultSkin;
        }

        public boolean isDefaultSkin() {
            return this.defaultSkin;
        }

        public int debugGetSkinBytesLen() {
            return (skinByteArray == null ? 0 : skinByteArray.length);
        }

        public int debugGetBaseVariant() {
            return this.baseVariant;
        }

        public void clearExplicitSkin() {
            this.skinIdentifier = null;
        }
    }

    public static class NameManager {
        String[] defaultModelNames = {"Charles", "Cade", "Henry", "Liam", "Rodney", "Nathaniel", "Elliot", "Julian", "Malcolm", "Tobias",
                "Wesley", "Felix", "Desmond", "Simon", "Miles", "Everett", "Dorian", "Quentin", "Cedric", "Adrian", "Roman", "Marcus", "Gideon", "Levi", "Jasper"};
        String[] slimModelNames = {"Evelyn", "Sarah", "Olivia", "Emma", "Alexia", "Amelia", "Celeste", "Lillian", "Joleen", "Rosalie",
                "Clara", "Vivienne", "Elena", "Margot", "Nora", "Daphne", "Fiona", "Genevieve", "Juliette", "Lucille", "Naomi", "Ivy", "Serena", "Vera", "Adelaide"};

        NPCEntity npcEntity;

        public NameManager(NPCEntity npcEntity) {
            this.npcEntity = npcEntity;
        }

        public void setRandomName(boolean slim) {
            if (slim)
                this.npcEntity.setCustomName(Text.literal(slimModelNames[Random.create().nextInt(slimModelNames.length)]));
            else
                this.npcEntity.setCustomName(Text.literal(defaultModelNames[Random.create().nextInt(defaultModelNames.length)]));

        }
    }
}