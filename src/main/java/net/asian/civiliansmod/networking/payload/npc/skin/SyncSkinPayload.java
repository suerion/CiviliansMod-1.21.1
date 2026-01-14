package net.asian.civiliansmod.networking.payload.npc.skin;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.util.ModCompat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SyncSkinPayload(int npcId, int skinVariant) implements CustomPayload {
    public static final CustomPayload.Id<SyncSkinPayload> ID = new CustomPayload.Id<>(Identifier.of(CiviliansMod.MOD_ID, "sync_skin_payload"));

    public static final PacketCodec<RegistryByteBuf, SyncSkinPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.INTEGER, SyncSkinPayload::npcId,
                    PacketCodecs.INTEGER, SyncSkinPayload::skinVariant,
                    SyncSkinPayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void handlePacket(ClientPlayNetworking.Context context) {
        World world = context.player().getWorld();
        Entity entity = world.getEntityById(this.npcId);

        if (entity instanceof NPCEntity npc) {
            npc.setTrackedSkinVariant(this.skinVariant);
            npc.getDataTracker().set(NPCEntity.HAS_CUSTOM_SKIN, false);
        }
    }
}
