package net.asian.civiliansmod.renderer;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.model.NPCModel;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
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
        super.updateRenderState(livingEntity, livingEntityRenderState, f);

        var skin = livingEntity.getSkinManager().getIdSkin();

        if (skin != null && skin.id() != null) {
            livingEntityRenderState.texture = skin.id();
            livingEntityRenderState.slim = skin.slim();
        } else {
            livingEntityRenderState.texture = null;
            livingEntityRenderState.slim = false;
        }

        //adding animations
        // hit animation
        livingEntityRenderState.handSwingProgress = livingEntity.getHandSwingProgress(f);

        // run animation
        livingEntityRenderState.limbSwingAnimationProgress = livingEntity.limbAnimator.getAnimationProgress(f);
        livingEntityRenderState.limbSwingAmplitude        = livingEntity.limbAnimator.getAmplitude(f);

        //item use animatione
        livingEntityRenderState.isUsingItem  = livingEntity.isUsingItem();
        livingEntityRenderState.itemUseTime  = livingEntity.getItemUseTime();
        livingEntityRenderState.activeHand   = livingEntity.getActiveHand();
        livingEntityRenderState.preferredArm = livingEntity.getMainArm();

        //sneaking and swimming
        livingEntityRenderState.isInSneakingPose = livingEntity.isInSneakingPose();
        livingEntityRenderState.isSwimming       = livingEntity.isSwimming();

        ArmedEntityRenderState.updateRenderState(livingEntity, livingEntityRenderState, this.itemModelResolver);

        if (CiviliansMod.DEBUG_RENDER) {
            CiviliansMod.LOGGER.info(
                    "[NPC/RenderState] id={} slim={} texture={} swing={} mainHandItem={} offHandItem={}",
                    livingEntity.getId(),
                    livingEntityRenderState.slim,
                    livingEntityRenderState.texture,
                    livingEntityRenderState.handSwingProgress,
                    livingEntityRenderState.getMainHandItemState(),
                    livingEntityRenderState.leftHandItemState
            );
        }
    }
}