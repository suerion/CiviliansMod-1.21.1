package net.asian.civiliansmod.networking.payload.npc.skin;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.entity.NPCEntity;
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

import java.util.Objects;
import java.util.UUID;

public record ChangeSkinPayload(UUID npcUuid, boolean slim, byte[] skin) implements CustomPayload {
    public static final CustomPayload.Id<ChangeSkinPayload> ID = new CustomPayload.Id<>(Identifier.of(CiviliansMod.MOD_ID, "npc_skin_edit"));

    public static final PacketCodec<RegistryByteBuf, ChangeSkinPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, ChangeSkinPayload::npcUuid,
            PacketCodecs.BOOLEAN, ChangeSkinPayload::slim,
            PacketCodecs.BYTE_ARRAY, ChangeSkinPayload::skin,
            ChangeSkinPayload::new
    );

    public ChangeSkinPayload(NPCEntity npc) {
        this(npc.getUuid(), npc.getSkinManager().isSlimModel(),Objects.requireNonNull(npc.getSkinManager().getSkinByteArray(), "Tried to send ChangeSkinPayload without skin bytes"));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void handlePacket(ServerPlayNetworking.Context context) {
        if (skin == null || skin.length == 0) return;
        if (skin.length > 256_000) return;

        context.server().execute(() -> {
            if (!(context.player().getWorld() instanceof ServerWorld world)) return;
            if (!(world.getEntity(this.npcUuid) instanceof NPCEntity entity)) return;

            entity.getSkinManager().setSkinByteArray(skin);
            entity.getSkinManager().setIdSkin(new SkinIdentifier(Identifier.of(CiviliansMod.MOD_ID, "npc_skin_" + entity.getUuid()), this.slim, true));
            entity.setTrackedSkinVariant(-1);

            entity.getDataTracker().set(NPCEntity.HAS_CUSTOM_SKIN, true);

            entity.calculateDimensions();
            entity.setVelocity(entity.getVelocity());

            for (ServerPlayerEntity player : world.getPlayers()) {
                ServerPlayNetworking.send(player, new ClientNpcSkinPayload(entity.getId(), this.slim, skin));
            }
            if (CiviliansMod.DEBUG_TEXTURE || CiviliansMod.DEBUG_NETWORK) {
                CiviliansMod.LOGGER.info(
                        "[SERVER/SKIN-SET] uuid={} HAS_CUSTOM_SKIN={} bytes={} slim={}",
                        entity.getUuid(),
                        entity.getDataTracker().get(NPCEntity.HAS_CUSTOM_SKIN),
                        skin.length,
                        slim
                );
            }
        });
    }
}