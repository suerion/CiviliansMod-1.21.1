package net.asian.civiliansmod.networking.payload.npc.dialogue;

import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.entity.NPCEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

public record MassRemoveDialoguePayload(
        int npcId,
        String language,
        NpcChat.ChatReason reason,
        List<String> dialoguesToRemove
) implements CustomPayload {
    
    public static final CustomPayload.Id<MassRemoveDialoguePayload> ID = new CustomPayload.Id<>(Identifier.of("civiliansmod", "mass_remove_dialogue"));

    public static final PacketCodec<RegistryByteBuf, MassRemoveDialoguePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, MassRemoveDialoguePayload::npcId,
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

    public static void handlePacket(MassRemoveDialoguePayload payload, ServerPlayerEntity player) {
        if (player.getWorld().getEntityById(payload.npcId()) instanceof NPCEntity npc) {
            npc.getChatManager().removeDialogues(
                    payload.language(),
                    payload.reason(),
                    payload.dialoguesToRemove()
            );
        }
    }
}