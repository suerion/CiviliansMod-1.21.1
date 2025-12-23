package net.asian.civiliansmod;

import net.asian.civiliansmod.entity.ModEntities;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.networking.CustomC2SNetworking;
import net.asian.civiliansmod.networking.NetworkPayloads;
import net.asian.civiliansmod.networking.payload.npc.dialogue.OpenScreenDialoguesPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class CiviliansMod implements ModInitializer {

    public static final String MOD_ID = "civiliansmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Map<UUID, String> playerLanguages = new HashMap<>();

    private static Boolean flashbackReplay = null;

    public static boolean isFlashbackReplay() {
        if (flashbackReplay != null) {
            return flashbackReplay;
        }

        try {
            Class<?> flashbackClass = Class.forName("com.moulberry.flashback.Flashback");
            Object result = flashbackClass.getMethod("isInReplay").invoke(null);
            flashbackReplay = result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            flashbackReplay = false;
        }

        return flashbackReplay;
    }

    public static void resetFlashbackCache() {
        flashbackReplay = null;
    }
  
    @Override
    public void onInitialize() {
        LOGGER.info("[CiviliansMod] Initializing CiviliansMod");

        FabricDefaultAttributeRegistry.register(ModEntities.NPC_ENTITY, NPCEntity.createAttributes());

        ModItems.registerModItems();

        NPCConversionHandler.register();

        NetworkPayloads.intialize();

        CustomC2SNetworking.intialize();

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof NPCEntity npc) {
                UUID playerId = player.getUuid();
                if (!npc.hasSentTo(playerId)) {
                    npc.markSentTo(playerId);
                    ServerPlayNetworking.send(player, new OpenScreenDialoguesPayload(npc.getId(), npc.getChatManager().getDialogues()));
                }
            }
        });

    }
}