package net.asian.civiliansmod.entity;

import com.mojang.serialization.Codec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.ModCompat;
import net.asian.civiliansmod.util.SkinIdentifier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;

import java.util.Optional;

public class SkinManager {

    private byte[] skinByteArray;
    private SkinIdentifier skinIdentifier;

    private int baseVariant;
    private boolean slim;
    private boolean defaultSkin;

    // Used to ensure we do not spam sync packets for the same entity.
    public boolean skinSynced;

    private final NPCEntity npcEntity;

    public SkinManager(NPCEntity npcEntity) {
        this.npcEntity = npcEntity;
        this.defaultSkin = true;

        // Important for replay mods (e.g. Flashback): do NOT assign random skins here.
        // Server-side default skin assignment is handled once in NPCEntity when appropriate.
        this.baseVariant = -1;
        this.slim = false;
        this.skinSynced = false;
    }

    public boolean isSlim() {
        return slim;
    }

    public void setSlim(boolean slim) {
        this.slim = slim;
    }

    public void setBaseVariant(int baseVariant) {
        this.baseVariant = baseVariant;
    }

    public void setSkinByteArray(byte[] skinByteArray) {
        this.skinByteArray = skinByteArray;
        this.defaultSkin = false;
        if (MinecraftClient.getInstance() != null) {
            uploadDynamicTexture();
        }
    }

    public void setIdSkin(SkinIdentifier skin) {
        this.skinIdentifier = skin;
    }

    public SkinIdentifier getIdSkin() {
        if (this.skinIdentifier != null) {
            return this.skinIdentifier;
        }

        // If baseVariant was never assigned, do not fall back to 0 (causes replay instability).
        if (this.baseVariant < 0) {
            if (ModCompat.isInReplayJoinPhase()) {
                return null;
            }
            // In replay contexts there is no server sync; use a deterministic, read-only fallback.
            if (ModCompat.isInReplay() && !NPCUtil.getSkins().isEmpty()) {
                long seed = npcEntity.getUuid().getLeastSignificantBits() ^ npcEntity.getUuid().getMostSignificantBits();
                int idx = (int) Math.floorMod(seed, NPCUtil.getSkins().size());
                return NPCUtil.getNPCTexture(idx);
            }
            return null;
        }

        return NPCUtil.getNPCTexture(baseVariant);
    }

    public SkinIdentifier getSkinIdentifier() {
        return this.skinIdentifier;
    }

    @Environment(EnvType.CLIENT)
    public void uploadDynamicTexture() {
        if (skinByteArray == null) return;

        try {
            NativeImage img = NativeImage.read(skinByteArray);
            Identifier id = Identifier.of("civiliansmod", "npc_skin_" + npcEntity.getUuid());

            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> id.getPath(), img);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);

            this.skinIdentifier = new SkinIdentifier(id, slim, true);
        } catch (Exception e) {
            CiviliansMod.LOGGER.error("Failed to upload NPC custom skin", e);
        }
    }

    void writeView(WriteView writeView) {
        writeView.putInt("basevariant", baseVariant);
        writeView.putBoolean("slim", slim);
        writeView.putBoolean("defaultSkin", defaultSkin);

        if (skinByteArray != null) {
            writeView.put("skin", Skin.CODEC, new Skin(skinByteArray));
        }
    }

    void readNbt(ReadView readView) {
        this.baseVariant = readView.getInt("basevariant", -1);
        this.slim = readView.getBoolean("slim", this.baseVariant > 43);
        this.defaultSkin = readView.getBoolean("defaultSkin", true);

        Optional<Skin> skin = readView.read("skin", Skin.CODEC);
        if (skin.isPresent()) {
            this.skinByteArray = skin.get().skin;
            this.defaultSkin = false;

            if (MinecraftClient.getInstance() != null) {
                uploadDynamicTexture();
            }
        }
    }

    public static class Skin {
        public static final Codec<Skin> CODEC = Codecs.BASE_64.xmap(Skin::new, s -> s.skin);
        public final byte[] skin;

        public Skin(byte[] skin) {
            this.skin = skin;
        }
    }
    public byte[] getSkinByteArray() {
        return skinByteArray;
    }

    public int getBaseVariant() {
        return baseVariant;
    }

    public boolean isSlimModel() {
        return slim;
    }
    public boolean isDefaultSkin() {
        return defaultSkin;
    }
    public void setDefaultSkin(boolean value) {
        this.defaultSkin = value;
    }
}
