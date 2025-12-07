package net.asian.civiliansmod.entity;

import com.mojang.serialization.Codec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.dynamic.Codecs;
import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.util.NPCUtil;
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

    private final NPCEntity npcEntity;

    public SkinManager(NPCEntity npcEntity) {
        this.npcEntity = npcEntity;
        this.defaultSkin = true;

        // random basevariant
        this.baseVariant = npcEntity.getRandom().nextInt(88);
        this.slim = this.baseVariant > 43;

        // random name
        if (npcEntity.nameManager != null) {
            npcEntity.nameManager.setRandomName(this.slim);
        } else {
            CiviliansMod.LOGGER.warn("[CiviliansMod] nameManager is null in SkinManager constructor");
        }
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

    @Environment(EnvType.CLIENT)
    public void setIdSkin(SkinIdentifier skin) {
        this.skinIdentifier = skin;
    }

    @Environment(EnvType.CLIENT)
    public SkinIdentifier getIdSkin() {
        if (this.skinIdentifier == null) {
            return NPCUtil.getNPCTexture(baseVariant);
        }
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
        this.baseVariant = readView.getInt("basevariant", 0);
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
