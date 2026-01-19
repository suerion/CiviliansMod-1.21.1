package net.asian.civiliansmod.renderer;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.model.NPCModel;
import net.asian.civiliansmod.util.ModCompat;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.SkinIdentifier;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class NPCRenderer extends MobEntityRenderer<NPCEntity, NPCRenderState, NPCModel> {
    // Entity model layers for default and slim models
    public static final EntityModelLayer DEFAULT_ENTITY_MODEL_LAYER =
            new EntityModelLayer(Identifier.of("civiliansmod", "npc_default"), "main");
    public static final EntityModelLayer SLIM_ENTITY_MODEL_LAYER =
            new EntityModelLayer(Identifier.of("civiliansmod", "npc_slim"), "main");

    // Cached models for performance
    private final NPCModel defaultModel;
    private final NPCModel slimModel;

    // Constructor
    public NPCRenderer(EntityRendererFactory.Context context) {
        // Set the default model and shadow size
        super(context, new NPCModel(context.getPart(DEFAULT_ENTITY_MODEL_LAYER), false), 0.5F);

        // Cache both default and slim models for reuse
        this.defaultModel = new NPCModel(context.getPart(DEFAULT_ENTITY_MODEL_LAYER), false);
        this.slimModel = new NPCModel(context.getPart(SLIM_ENTITY_MODEL_LAYER), true);
        this.addFeature(new HeldItemFeatureRenderer<>(this));
    }

    @Override
    public NPCRenderState createRenderState() {
        return new NPCRenderState();
    }

    /**
     * Adjusts the rendering model (default vs. slim) dynamically based on the entity's variant.
     */
    @Override
    public void render(NPCRenderState livingEntityRenderState, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i) {
        this.model = livingEntityRenderState.slim ? slimModel : defaultModel;
        super.render(livingEntityRenderState, matrixStack, vertexConsumerProvider, i);
    }

    /**
     * Scales the NPC entity slightly for both slim and default models.
     */
    @Override
    protected void scale(NPCRenderState livingEntityRenderState, MatrixStack matrixStack) {
        float scale = 0.945F;
        matrixStack.scale(scale, scale, scale);
        super.scale(livingEntityRenderState, matrixStack);
    }

    /**
     * Dynamically assigns the appropriate texture based on the NPC's variant.
     */
    @Override
    public Identifier getTexture(NPCRenderState livingEntityRenderState) {
        return livingEntityRenderState.texture;
    }

    @Override
    public void updateRenderState(NPCEntity livingEntity, NPCRenderState livingEntityRenderState, float f) {
        if (CiviliansMod.DEBUG_RENDER) {
            CiviliansMod.LOGGER.info(
                    "[RenderState/START] id={} uuid={} replay={}",
                    livingEntity.getId(),
                    livingEntity.getUuid(),
                    ModCompat.isInReplay()
            );
        }
        super.updateRenderState(livingEntity, livingEntityRenderState, f);

        boolean resolved = false;

        if (livingEntity.getSkinManager().getSkinByteArray() != null && livingEntity.getSkinManager().getIdSkin() == null) {

            if (CiviliansMod.DEBUG_RENDER) {
                CiviliansMod.LOGGER.warn(
                        "[RenderState/FIXUP] uuid={} had bytes but no idSkin",
                        livingEntity.getUuid()
                );
            }

            Identifier id = Identifier.of(CiviliansMod.MOD_ID, "npc_skin_" + livingEntity.getUuid());

            livingEntity.getSkinManager().setIdSkin(
                    new SkinIdentifier(id, false, true)
            );
        }
        if (CiviliansMod.DEBUG_RENDER) {
            CiviliansMod.LOGGER.info(
                    "[RENDER/CHECK] uuid={} skinId={} bytes={}",
                    livingEntity.getUuid(),
                    livingEntity.getSkinManager().getIdSkin(),
                    livingEntity.getSkinManager().getSkinByteArray() != null ? livingEntity.getSkinManager().getSkinByteArray().length : -1
            );
        }
        var skin = livingEntity.getSkinManager().getIdSkin();
        if (skin != null && skin.id() != null) {
            if (CiviliansMod.DEBUG_RENDER) {
                CiviliansMod.LOGGER.info(
                        "[RenderState/CUSTOM] id={} texture={} slim={}",
                        livingEntity.getId(),
                        skin.id(),
                        skin.slim()
                );

            }
            livingEntityRenderState.texture = skin.id();
            livingEntityRenderState.slim = skin.slim();
            resolved = true;
        }

        if (!resolved) {

            //  REPLAY-SAFE: use tracked skin variant, NEVER vanilla default
            int tracked = livingEntity.getTrackedSkinVariant();
            if (tracked >= 0) {
                if (CiviliansMod.DEBUG_RENDER) {
                    CiviliansMod.LOGGER.info(
                            "[RenderState/FALLBACK-VARIANT] id={} variant={}",
                            livingEntity.getId(),
                            tracked
                    );
                }
                var fallback = NPCUtil.getNPCTexture(tracked);
                if (fallback != null && fallback.id() != null) {
                    livingEntityRenderState.texture = fallback.id();
                    livingEntityRenderState.slim = fallback.slim();
                    resolved = true;
                }
            }
        }

        if (!resolved) {
            // absolute last resort – should basically never happen
            if (CiviliansMod.DEBUG_RENDER) {
                CiviliansMod.LOGGER.error(
                        "[RenderState/VANILLA-FALLBACK] id={} uuid={}",
                        livingEntity.getId(),
                        livingEntity.getUuid()
                );
            }
            var vanilla = DefaultSkinHelper.getSkinTextures(livingEntity.getUuid());
            livingEntityRenderState.texture = vanilla.texture();
            livingEntityRenderState.slim =
                    vanilla.model() == SkinTextures.Model.SLIM;
        }

        livingEntityRenderState.handSwingProgress = livingEntity.getHandSwingProgress(f);
        livingEntityRenderState.limbSwingAnimationProgress = livingEntity.limbAnimator.getAnimationProgress(f);
        livingEntityRenderState.limbSwingAmplitude = livingEntity.limbAnimator.getAmplitude(f);

        livingEntityRenderState.isUsingItem  = livingEntity.isUsingItem();
        livingEntityRenderState.itemUseTime  = livingEntity.getItemUseTime();
        livingEntityRenderState.activeHand   = livingEntity.getActiveHand();
        livingEntityRenderState.preferredArm = livingEntity.getMainArm();

        livingEntityRenderState.isInSneakingPose = livingEntity.isInSneakingPose();
        livingEntityRenderState.isSwimming       = livingEntity.isSwimming();

        ArmedEntityRenderState.updateRenderState(livingEntity, livingEntityRenderState, this.itemModelResolver);

        if (CiviliansMod.DEBUG_RENDER) {
            CiviliansMod.LOGGER.info(
                    "[RENDER/FINAL] id={} texture={} slim={} replay={}",
                    livingEntity.getId(),
                    livingEntityRenderState.texture,
                    livingEntityRenderState.slim,
                    ModCompat.isInReplay()
            );
        }
    }
}