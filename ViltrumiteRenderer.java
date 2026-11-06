package com.viltrumite.boss.client;

import com.viltrumite.boss.ViltrumiteMod;
import com.viltrumite.boss.entity.ViltrumiteEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class ViltrumiteRenderer extends BipedEntityRenderer<ViltrumiteEntity, ViltrumiteModel> {
	private static final Identifier TEXTURE = new Identifier(ViltrumiteMod.MOD_ID, "textures/entity/viltrumite.png");

	public ViltrumiteRenderer(EntityRendererFactory.Context ctx) {
		super(ctx, new ViltrumiteModel(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
	}

	@Override
	public Identifier getTexture(ViltrumiteEntity entity) {
		return TEXTURE;
	}

	@Override
	protected void scale(ViltrumiteEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(1.1f, 1.1f, 1.1f);
	}

	/** Ҳангоми парвоз бадан ба пеш хам мешавад (мисли суперман). */
	@Override
	protected void setupTransforms(ViltrumiteEntity entity, MatrixStack matrices, float animationProgress,
			float bodyYaw, float tickDelta) {
		super.setupTransforms(entity, matrices, animationProgress, bodyYaw, tickDelta);
		float lean = MathHelper.lerp(tickDelta, entity.prevLean, entity.lean);
		if (lean > 0.01f) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f * lean));
			matrices.translate(0.0f, -lean, lean * 0.95f);
		}
	}
}
