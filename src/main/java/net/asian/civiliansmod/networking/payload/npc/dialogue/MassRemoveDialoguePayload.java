package net.asian.civiliansmod.networking.payload.npc.dialogue;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.NPCEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record MassRemoveDialoguePayload(
        UUID npcUuid,
        String language,
        NpcChat.ChatReason reason,
        List<String> dialoguesToRemove
) implements CustomPayload {
    
    public static final CustomPayload.Id<MassRemoveDialoguePayload> ID = new CustomPayload.Id<>(Identifier.of("civiliansmod", "mass_remove_dialogue"));

    public static final PacketCodec<RegistryByteBuf, MassRemoveDialoguePayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, MassRemoveDialoguePayload::npcUuid,
            PacketCodecs.STRING, MassRemoveDialoguePayload::language,
            PacketCodecs.indexed(
                    i -> NpcChat.ChatReason.values()[i],
                    NpcChat.ChatReason::ordinal
            ), MassRemoveDialoguePayload::reason,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), MassRemoveDialoguePayload::dialoguesToRemove,
            MassRemoveDialoguePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void handlePacket(MassRemoveDialoguePayload payload, ServerPlayNetworking.Context context) {

        ServerPlayerEntity player = context.player();
        Entity entity = player.getWorld().getEntity(payload.npcUuid());
        if (!(entity instanceof NPCEntity npc)) return;

        List<String> list = npc.getChatManager()
                .getTranslatedDialogues(payload.language)
                .computeIfAbsent(payload.reason(), (o) -> new ArrayList<>());

        list.removeIf(payload.dialoguesToRemove()::contains);
        npc.getChatManager().markDialoguesDirty(player.getUuid());
    }
}