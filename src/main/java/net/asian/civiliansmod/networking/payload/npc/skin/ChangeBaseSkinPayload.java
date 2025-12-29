package net.asian.civiliansmod.networking.payload.npc.skin;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.SkinIdentifier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

public record ChangeBaseSkinPayload(UUID npcUuid, int baseVariant) implements CustomPayload {
    public static final CustomPayload.Id<ChangeBaseSkinPayload> ID = new CustomPayload.Id<>(Identifier.of(CiviliansMod.MOD_ID, "npc_base_skin_edit"));

    public static final PacketCodec<RegistryByteBuf, ChangeBaseSkinPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, ChangeBaseSkinPayload::npcUuid,
            PacketCodecs.INTEGER, ChangeBaseSkinPayload::baseVariant,
            ChangeBaseSkinPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void handlePacket(ServerPlayNetworking.Context context) {
        if (!(context.player().getWorld() instanceof ServerWorld world)) return;
        if (!(world.getEntity(this.npcUuid) instanceof NPCEntity entity)) return;

        int skinIdx = this.baseVariant;

        // 1) baseVariant speichern (Server-Truth)
        entity.getSkinManager().setBaseVariant(skinIdx);

        // 2) trackedVariant synchronisieren
        entity.getDataTracker().set(NPCEntity.getTrackedSkinVariant(), skinIdx);

        // 3) WICHTIG: skinIdentifier NICHT anfassen!
        // → der kommt entweder:
        //   a) vom Client via ChangeSkinPayload
        //   b) aus NBT beim nächsten Load

        CiviliansMod.LOGGER.info(
                "[NPC/SKIN/BASEVARIANT] id={} baseVariant={}",
                entity.getId(),
                skinIdx
        );
    }
}