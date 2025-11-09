package net.asian.civiliansmod.renderer;

import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.model.NPCModel;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
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
    }

    @Override
    public NPCRenderState createRenderState() {
        return new NPCRenderState();
    }

    /**
     * Adjusts the rendering model (default vs. slim) dynamically based on the entity's variant.
     */
    @Override
    public void render(NPCRenderState state, MatrixStack matrices,
                       OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        this.model = state.slim ? slimModel : defaultModel;
        super.render(state, matrices, queue, cameraState);
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

        var skinManager = livingEntity.getSkinManager();
        var id = skinManager.getIdSkin();

        if (id != null) {
            livingEntityRenderState.texture = id.id();
            livingEntityRenderState.slim = id.slim();
        } else {
            // fallback
            var fallback = net.asian.civiliansmod.util.NPCUtil.getNPCTexture(skinManager.getBaseVariant());
            livingEntityRenderState.texture = fallback.id();
            livingEntityRenderState.slim = fallback.slim();

            net.asian.civiliansmod.CiviliansMod.LOGGER.warn("[CiviliansMod] Renderer fallback texture for NPC {} -> {}", livingEntity.getUuid(), fallback.id()
            );
        }
    }
}