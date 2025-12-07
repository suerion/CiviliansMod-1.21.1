package net.asian.civiliansmod.networking.payload.npc.skin;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.SkinIdentifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SyncSkinPayload(int npcId, Identifier textureId, boolean slim) implements CustomPayload {
    public static final CustomPayload.Id<SyncSkinPayload> ID = new CustomPayload.Id<>(Identifier.of(CiviliansMod.MOD_ID, "sync_skin_payload"));

    public static final PacketCodec<RegistryByteBuf, SyncSkinPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, SyncSkinPayload::npcId,
            Identifier.PACKET_CODEC, SyncSkinPayload::textureId,
            PacketCodecs.BOOLEAN, SyncSkinPayload::slim,
            SyncSkinPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void handlePacket(ClientPlayNetworking.Context context) {
        ClientWorld clientWorld = context.player().clientWorld;
        Entity entityById = clientWorld.getEntityById(this.npcId);

        SkinIdentifier skinId = new SkinIdentifier(textureId, slim, false);

        if (entityById == null) {
            NPCUtil.waitingSync.put(npcId, skinId);
            return;
        }
        if(entityById instanceof NPCEntity npcEntity) {
            npcEntity.getSkinManager().setIdSkin(skinId);
            npcEntity.getSkinManager().setDefaultSkin(true);
        }
    }
}
