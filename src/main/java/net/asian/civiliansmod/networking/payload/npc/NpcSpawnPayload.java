package net.asian.civiliansmod.networking.payload.npc;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.entity.NPCEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public record NpcSpawnPayload(int npcId, String dialogues, byte[] skin) implements CustomPayload {
    public static final CustomPayload.Id<NpcSpawnPayload> ID = new CustomPayload.Id<>(Identifier.of(CiviliansMod.MOD_ID, "spawn_payload"));

    public static final PacketCodec<RegistryByteBuf, NpcSpawnPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, NpcSpawnPayload::npcId,
            PacketCodecs.STRING, NpcSpawnPayload::dialogues,
            PacketCodecs.BYTE_ARRAY, NpcSpawnPayload::skin,
            NpcSpawnPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void handlePacket(ClientPlayNetworking.Context context) {
        World world = context.player().getWorld();
        if (!(world.getEntityById(this.npcId) instanceof NPCEntity entity)) return;
        if (skin.length != 16384) return;
        entity.getSkinManager().setSkinByteArray(skin);
    }
}
