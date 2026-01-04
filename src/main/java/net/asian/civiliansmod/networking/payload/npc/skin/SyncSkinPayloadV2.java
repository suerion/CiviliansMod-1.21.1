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

public record SyncSkinPayloadV2(int npcId,int skinVariant) implements CustomPayload {
    public static final CustomPayload.Id<SyncSkinPayloadV2> ID = new CustomPayload.Id<>(Identifier.of(CiviliansMod.MOD_ID, "sync_skin_payload_v2"));

    public static final PacketCodec<RegistryByteBuf, SyncSkinPayloadV2> CODEC = PacketCodec.tuple(
                    PacketCodecs.INTEGER, SyncSkinPayloadV2::npcId,
                    PacketCodecs.INTEGER, SyncSkinPayloadV2::skinVariant,
                    SyncSkinPayloadV2::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

    public void handlePacket(ClientPlayNetworking.Context context) {
        Entity entity = context.player().clientWorld.getEntityById(npcId);
        if (!(entity instanceof NPCEntity npc)) return;

        npc.setTrackedSkinVariant(skinVariant);
    }
}
