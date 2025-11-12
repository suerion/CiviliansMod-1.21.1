package net.asian.civiliansmod.networking;

import net.asian.civiliansmod.networking.payload.npc.dialogue.*;
import net.asian.civiliansmod.networking.payload.npc.skin.ChangeBaseSkinPayload;
import net.asian.civiliansmod.networking.payload.npc.skin.ChangeSkinPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class CustomC2SNetworking {

    static {
        ServerPlayNetworking.registerGlobalReceiver(NPCDataPayload.ID, NPCDataPayload::handlePacket);
        ServerPlayNetworking.registerGlobalReceiver(AddDialoguePayload.ID, AddDialoguePayload::handlePacket);
        ServerPlayNetworking.registerGlobalReceiver(EditDialoguePayload.ID, EditDialoguePayload::handlePacket);
        ServerPlayNetworking.registerGlobalReceiver(RemoveDialoguePayload.ID, RemoveDialoguePayload::handlePacket);
        ServerPlayNetworking.registerGlobalReceiver(ChangeSkinPayload.ID, ChangeSkinPayload::handlePacket);
        ServerPlayNetworking.registerGlobalReceiver(PlayerLanguagePayload.ID, PlayerLanguagePayload::handlePacket);
        ServerPlayNetworking.registerGlobalReceiver(ClientDialogueSyncPayload.ID, ClientDialogueSyncPayload::handlePacket);
        ServerPlayNetworking.registerGlobalReceiver(ChangeBaseSkinPayload.ID, ChangeBaseSkinPayload::handlePacket);
        // NEW
        ServerPlayNetworking.registerGlobalReceiver(MassRemoveDialoguePayload.ID,
                (payload, context) -> context.server().execute(() -> MassRemoveDialoguePayload.handlePacket(payload, context.player())));
    }
    public static void intialize() {
        // This method is called to ensure the static block executes.
    }

}