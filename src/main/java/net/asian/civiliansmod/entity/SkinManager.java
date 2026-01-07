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
        this.skinSynced = false;
    }

    // LEGACY ONLY – must never be written after migration
    @Deprecated(forRemoval = false)
    public void setBaseVariant(int baseVariant) {
        this.baseVariant = baseVariant;
    }

    public void setSkinByteArray(byte[] skinByteArray) {
        this.skinByteArray = skinByteArray;
        this.defaultSkin = false;
    }

    public void setIdSkin(SkinIdentifier skin) {
        this.skinIdentifier = skin;
        this.defaultSkin = skin != null && !skin.custom();
    }

    public SkinIdentifier getIdSkin() {
        return this.skinIdentifier;
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

            boolean slim = false;

            if (npcEntity.getTrackedSkinVariant() >= 0) {
                slim = npcEntity.getTrackedSkinVariant() > 43;
            }

            this.skinIdentifier = new SkinIdentifier(id, slim, true);
            this.defaultSkin = false;
        } catch (Exception e) {
            CiviliansMod.LOGGER.error("Failed to upload NPC custom skin", e);
        }
    }

    void writeView(WriteView writeView) {
        writeView.putInt("basevariant", baseVariant);
        writeView.putBoolean("defaultSkin", defaultSkin);

        if (skinByteArray != null) {
            writeView.put("skin", Skin.CODEC, new Skin(skinByteArray));
        }
    }

    void readNbt(ReadView readView) {
        this.baseVariant = readView.getInt("basevariant", -1);
        this.defaultSkin = readView.getBoolean("defaultSkin", true);

        Optional<Skin> skin = readView.read("skin", Skin.CODEC);
        if (skin.isPresent()) {
            this.skinByteArray = skin.get().skin;
            this.defaultSkin = false;
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
        if (skinIdentifier != null) {
            return skinIdentifier.slim();
        }
        int tracked = npcEntity.getTrackedSkinVariant();
        return tracked >= 0 && tracked > 43;
    }

    public boolean isDefaultSkin() {
        return defaultSkin;
    }

    public void setDefaultSkin(boolean value) {
        this.defaultSkin = value;
    }
}
