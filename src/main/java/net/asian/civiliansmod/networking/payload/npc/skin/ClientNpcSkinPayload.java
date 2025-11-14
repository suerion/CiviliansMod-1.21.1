package net.asian.civiliansmod.networking.payload.npc.skin;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.SkinIdentifier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record ClientNpcSkinPayload(int npcId, boolean slim, byte[] skin) implements CustomPayload {

    public static final CustomPayload.Id<ClientNpcSkinPayload> ID =
            new CustomPayload.Id<>(Identifier.of(CiviliansMod.MOD_ID, "client_npc_skin_update"));

    // add texture caching
    private static final Map<Integer, SkinIdentifier> NPC_TEXTURE_CACHE = new HashMap<>();

    public static final PacketCodec<RegistryByteBuf, ClientNpcSkinPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, ClientNpcSkinPayload::npcId,
            PacketCodecs.BOOLEAN, ClientNpcSkinPayload::slim,
            PacketCodecs.BYTE_ARRAY, ClientNpcSkinPayload::skin,
            ClientNpcSkinPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    @Environment(EnvType.CLIENT)
    public void handlePacket(ClientPlayNetworking.Context context) {
        context.client().execute(() -> {

            ClientWorld clientWorld = context.player().clientWorld;
            Entity entity = clientWorld.getEntityById(this.npcId);

            try {

                // check if cached
                SkinIdentifier cached = NPC_TEXTURE_CACHE.get(npcId);

                if (cached != null) {
                    CiviliansMod.LOGGER.info("[Client] Using cached texture for NPC {}", npcId);

                    if (entity instanceof NPCEntity npc) {
                        npc.getSkinManager().setSkinByteArray(skin());
                        npc.getSkinManager().setSlim(slim());
                        npc.getSkinManager().setIdSkin(cached);
                        npc.getSkinManager().setDefaultSkin(false);
                        npc.refreshSkinModel();
                    }
                    return;
                }

                // only on first time chang
                NativeImage image = NativeImage.read(skin);
                if (image.getWidth() != 64 || image.getHeight() != 64) {
                    CiviliansMod.LOGGER.error("[Client] Invalid skin size for NPC {}", npcId);
                    return;
                }

                String texName = "npcskin_cache_" + npcId;
                Identifier texId = Identifier.of(CiviliansMod.MOD_ID, texName);

                NativeImageBackedTexture texture =
                        new NativeImageBackedTexture(() -> texName, image);

                MinecraftClient.getInstance().getTextureManager().registerTexture(texId, texture);

                SkinIdentifier skinId = new SkinIdentifier(texId, slim, true);

                image.close();

                // save to cache
                NPC_TEXTURE_CACHE.put(npcId, skinId);

                if (entity instanceof NPCEntity npc) {
                    npc.getSkinManager().setSkinByteArray(skin());
                    npc.getSkinManager().setSlim(slim());
                    npc.getSkinManager().setIdSkin(skinId);
                    npc.getSkinManager().setDefaultSkin(false);
                    npc.refreshSkinModel();
                }

            } catch (Exception e) {
                CiviliansMod.LOGGER.error("[Client] Error decoding NPC skin", e);
            }
        });
    }
}
