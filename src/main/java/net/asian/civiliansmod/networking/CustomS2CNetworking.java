package net.asian.civiliansmod.networking;

import net.asian.civiliansmod.networking.payload.npc.NpcSpawnPayload;
import net.asian.civiliansmod.networking.payload.npc.dialogue.*;
import net.asian.civiliansmod.networking.payload.npc.skin.ClientNpcSkinPayload;
import net.asian.civiliansmod.networking.payload.npc.skin.SyncSkinPayloadV1;
import net.asian.civiliansmod.networking.payload.npc.skin.SyncSkinPayloadV2;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class CustomS2CNetworking {
    static {
        ClientPlayNetworking.registerGlobalReceiver(OpenScreenDialoguesPayload.ID, OpenScreenDialoguesPayload::handlePacket);
        ClientPlayNetworking.registerGlobalReceiver(ClientNpcSkinPayload.ID, ClientNpcSkinPayload::handlePacket);
        ClientPlayNetworking.registerGlobalReceiver(SyncSkinPayloadV1.ID, SyncSkinPayloadV1::handlePacket);
        ClientPlayNetworking.registerGlobalReceiver(SyncSkinPayloadV2.ID, SyncSkinPayloadV2::handlePacket);
        ClientPlayNetworking.registerGlobalReceiver(NpcSpawnPayload.ID, NpcSpawnPayload::handlePacket);
        ClientPlayNetworking.registerGlobalReceiver(DialogueSyncPayload.ID, DialogueSyncPayload::handlePacket);
    }
    public static void intialize() {
    }
}
