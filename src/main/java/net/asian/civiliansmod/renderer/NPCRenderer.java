package net.asian.civiliansmod.renderer;

import net.asian.civiliansmod.CiviliansMod;
import net.asian.civiliansmod.entity.NPCEntity;
import net.asian.civiliansmod.model.NPCModel;
import net.asian.civiliansmod.util.NPCUtil;
import net.asian.civiliansmod.util.SkinIdentifier;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
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
    public Identifier getTexture(NPCRenderState state) {
        return state.texture != null
                ? state.texture
                : DefaultSkinHelper.getTexture();
    }

    @Override
    public void updateRenderState(NPCEntity livingEntity, NPCRenderState state, float tickDelta) {

        // 🔒 FLASHBACK = IMMER DEFAULT
        if (CiviliansMod.isFlashbackReplay()) {
            var vanilla = DefaultSkinHelper.getSkinTextures(livingEntity.getUuid());
            state.texture = vanilla.texture();
            state.slim = vanilla.model() == SkinTextures.Model.SLIM;
            return;
        }

        // ⬇️ NORMALER SPIELBETRIEB
        super.updateRenderState(livingEntity, state, tickDelta);

        // 1️⃣ DataTracker hat Vorrang
        int trackedVariant = livingEntity.getDataTracker().get(NPCEntity.getTrackedSkinVariant());
        if (trackedVariant >= 0) {
            SkinIdentifier skin = NPCUtil.getNPCTexture(trackedVariant);
            if (skin != null) {
                state.texture = skin.id();
                state.slim = skin.slim();
                return;
            }
        }

        // 2️⃣ SkinManager (Custom / gesetzt)
        SkinIdentifier skin = livingEntity.getSkinManager().getIdSkin();
        if (skin != null && skin.id() != null) {
            state.texture = skin.id();
            state.slim = skin.slim();
            return;
        }

        // 3️⃣ Mod-Fallback
        SkinIdentifier fallback = NPCUtil.getNPCTexture(0);
        if (fallback != null) {
            state.texture = fallback.id();
            state.slim = fallback.slim();
            return;
        }

        // 4️⃣ Vanilla als letzter Notfall
        var vanilla = DefaultSkinHelper.getSkinTextures(livingEntity.getUuid());
        state.texture = vanilla.texture();
        state.slim = vanilla.model() == SkinTextures.Model.SLIM;
    }
}