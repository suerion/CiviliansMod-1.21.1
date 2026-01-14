package net.asian.civiliansmod.networking.payload.npc.skin;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.util.ModCompat;
import net.asian.civiliansmod.util.SkinIdentifier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record ClientNpcSkinPayload(int npcId, boolean slim, byte[] skin) implements CustomPayload {

    public static final CustomPayload.Id<ClientNpcSkinPayload> ID =
            new CustomPayload.Id<>(Identifier.of(CiviliansMod.MOD_ID, "client_npc_skin_update"));

    // add texture caching
    private static final Map<UUID, byte[]> NPC_SKIN_BYTES_CACHE = new HashMap<>();

    public static final PacketCodec<RegistryByteBuf, ClientNpcSkinPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, ClientNpcSkinPayload::npcId,
            PacketCodecs.BOOLEAN, ClientNpcSkinPayload::slim,
            PacketCodecs.BYTE_ARRAY, ClientNpcSkinPayload::skin,
            ClientNpcSkinPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    public static byte[] getCachedSkin(UUID uuid) {
        return NPC_SKIN_BYTES_CACHE.get(uuid);
    }

    @Environment(EnvType.CLIENT)
    public void handlePacket(ClientPlayNetworking.Context context) {
        if (CiviliansMod.DEBUG_NETWORK) {
            CiviliansMod.LOGGER.info(
                    "[ClientNpcSkinPayload] npcId={} bytes={}",
                    this.npcId,
                    this.skin != null ? this.skin.length : -1
            );
        }

        if (this.skin == null || this.skin.length == 0) {
            CiviliansMod.LOGGER.warn("[Client] Received empty skin packet for npcId={}", this.npcId);
            return;
        }

        context.client().execute(() -> {

            World world = context.player().getWorld();
            Entity entity = world.getEntityById(this.npcId);

            try {
                if (entity instanceof NPCEntity npc) {
                    if (CiviliansMod.DEBUG_NETWORK) {
                        CiviliansMod.LOGGER.info(
                                "[ClientNpcSkinPayload/APPLY] uuid={} bytes={} slim={}",
                                npc.getUuid(),
                                this.skin.length,
                                this.slim
                        );
                    }

                    UUID key = npc.getUuid();

                    // Cache bytes
                    NPC_SKIN_BYTES_CACHE.put(key, this.skin);

                    Identifier id = Identifier.of(CiviliansMod.MOD_ID,"npc_skin_" + npc.getUuid());
                    npc.getSkinManager().setIdSkin(new SkinIdentifier(id, this.slim, true));
                    npc.getSkinManager().setSkinByteArray(this.skin);
                    npc.refreshSkinModel();
                    CiviliansMod.LOGGER.info(
                            "[CLIENT-SKIN-APPLY] uuid={} bytes={} custom={} default={}",
                            npc.getUuid(),
                            this.skin.length,
                            npc.getSkinManager().getIdSkin() != null && npc.getSkinManager().getIdSkin().custom(),
                            npc.getSkinManager().isDefaultSkin()
                    );
                }
            } catch (Exception e) {
                CiviliansMod.LOGGER.error("[Client] Error decoding NPC skin", e);
            }
        });
    }
}
