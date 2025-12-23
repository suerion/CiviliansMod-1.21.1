package net.asian.civiliansmod;

import net.asian.civiliansmod.chat.NpcChat;
import net.asian.civiliansmod.custom_skins.SkinFolderManager;
import net.asian.civiliansmod.entity.ModEntities;
import net.asian.civiliansmod.model.NPCModel;
import net.asian.civiliansmod.networking.CustomS2CNetworking;
import net.asian.civiliansmod.networking.PlayerLanguagePayload;
import net.asian.civiliansmod.renderer.NPCRenderer;
import net.asian.civiliansmod.util.FolderUtil;
import net.asian.civiliansmod.util.NPCUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public class CiviliansModClient implements ClientModInitializer {

    public static final EntityModelLayer WIDE_ENTITY_MODEL_LAYER =
            new EntityModelLayer(Identifier.of("civiliansmod", "npc_default"), "main");

    public static final EntityModelLayer SLIM_ENTITY_MODEL_LAYER =
            new EntityModelLayer(Identifier.of("civiliansmod", "npc_slim"), "main");

    @Override
    public void onInitializeClient() {

        SkinFolderManager.register();

        EntityRendererRegistry.register(ModEntities.NPC_ENTITY, NPCRenderer::new);

        EntityModelLayerRegistry.registerModelLayer(
                WIDE_ENTITY_MODEL_LAYER,
                () -> TexturedModelData.of(
                        NPCModel.getTexturedModelData(Dilation.NONE, false),
                        64,
                        64
                )
        );

        EntityModelLayerRegistry.registerModelLayer(
                SLIM_ENTITY_MODEL_LAYER,
                () -> TexturedModelData.of(
                        NPCModel.getTexturedModelData(Dilation.NONE, true),
                        64,
                        64
                )
        );

        CustomS2CNetworking.intialize();

          ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {

            CiviliansMod.LOGGER.info("[CiviliansMod] Client JOIN");

            FolderUtil.init();
            SkinFolderManager.register();

            String lang = client.getLanguageManager().getLanguage();
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(lang);
            sender.sendPacket(new PlayerLanguagePayload(client.player.getUuid(), lang));

            boolean isFlashbackReplay = false;


            try {
                Class<?> flashbackClass = Class.forName("com.moulberry.flashback.Flashback");
                Object result = flashbackClass.getMethod("isInReplay").invoke(null);
                isFlashbackReplay = result instanceof Boolean && (Boolean) result;
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                CiviliansMod.LOGGER.warn("[CiviliansMod] Flashback check failed", t);
            }


            if (isFlashbackReplay) {
                CiviliansMod.LOGGER.info("[CiviliansMod] Flashback replay detected – delayed skin refresh");
                MinecraftClient.getInstance().execute(NPCUtil::refreshTextures);
            } else {
                CiviliansMod.LOGGER.info("[CiviliansMod] Normal join – skin refresh");
                NPCUtil.refreshTextures();
            }

            NpcChat.registerChat();
        });

        CiviliansMod.LOGGER.info("[CiviliansMod] Client initialized");
    }
}