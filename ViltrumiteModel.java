package com.viltrumite.boss.client;

import com.viltrumite.boss.entity.ViltrumiteEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.math.MathHelper;

/** Модели оддии бозингар + позаҳои хоси ҳар қобилият. Бе GeckoLib, пас FPS кам мегирад. */
public class ViltrumiteModel extends PlayerEntityModel<ViltrumiteEntity> {
	public ViltrumiteModel(ModelPart root) {
		super(root, false);
	}

	@Override
	public void setAngles(ViltrumiteEntity e, float limbAngle, float limbDistance, float animationProgress,
			float headYaw, float headPitch) {
		super.setAngles(e, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

		float t = animationProgress - e.stateStartAge;   // тикҳо аз оғози ҳолат
		switch (e.getState()) {
			case ViltrumiteEntity.ST_GRAB -> {
				float l = e.lean;
				this.rightArm.pitch = MathHelper.lerp(l, -1.45f, -2.9f);
				this.rightArm.yaw = -0.15f;
				this.rightArm.roll = 0f;
				this.leftArm.pitch = MathHelper.lerp(l, -3.0f, -3.1f);
				this.leftArm.yaw = 0f;
				this.leftArm.roll = 0f;
				this.leftLeg.pitch = 0.15f;
				this.rightLeg.pitch = -0.05f;
			}
			case ViltrumiteEntity.ST_THROW -> {
				float p = MathHelper.clamp(t / 8f, 0f, 1f);
				this.rightArm.pitch = MathHelper.lerp(p, -2.9f, -0.2f);
				this.rightArm.yaw = -0.1f;
				this.leftArm.pitch = 0.6f;
				this.leftArm.roll = -0.2f;
			}
			case ViltrumiteEntity.ST_BLOCK -> {
				this.rightArm.pitch = -1.1f;
				this.rightArm.yaw = 0.6f;
				this.leftArm.pitch = -1.1f;
				this.leftArm.yaw = -0.6f;
			}
			case ViltrumiteEntity.ST_PUNCH -> {
				float r1 = swing(t, 4f);
				float r2 = swing(t, 9f);
				this.rightArm.pitch = -0.9f - 0.8f * r1;
				this.rightArm.yaw = -0.2f;
				this.leftArm.pitch = -0.9f - 0.8f * r2;
				this.leftArm.yaw = 0.2f;
				this.body.yaw = (r1 - r2) * 0.45f;
				this.head.yaw += this.body.yaw * -0.5f;
			}
			case ViltrumiteEntity.ST_SLAM -> {
				boolean landed = e.isOnGround() && t > 7f;
				float a = landed ? 0.3f : -3.0f;
				this.rightArm.pitch = a;
				this.leftArm.pitch = a;
				this.rightArm.yaw = 0f;
				this.leftArm.yaw = 0f;
			}
			case ViltrumiteEntity.ST_BLOCKS -> {
				float w = MathHelper.sin(animationProgress * 0.3f) * 0.12f;
				this.rightArm.pitch = -2.9f + w;
				this.leftArm.pitch = -2.9f - w;
				this.rightArm.roll = 0.5f;
				this.leftArm.roll = -0.5f;
			}
			case ViltrumiteEntity.ST_DASH -> {
				float r1 = swing(t, 6f);
				float r2 = swing(t, 11f);
				float r3 = swing(t, 16f);
				this.rightArm.pitch = 1.0f - 2.6f * Math.max(r1, r3);
				this.leftArm.pitch = 1.0f - 2.6f * r2;
				this.rightArm.yaw = 0f;
				this.leftArm.yaw = 0f;
			}
			case ViltrumiteEntity.ST_SUMMON -> {
				this.rightArm.pitch = 0f;
				this.leftArm.pitch = 0f;
				this.rightArm.roll = 1.4f;
				this.leftArm.roll = -1.4f;
				this.head.pitch = -0.35f;
			}
			default -> { }
		}

		// ҳисобкунии қабатҳои дуюм (остин, шим, куртка, кулоҳ)
		this.leftSleeve.copyTransform(this.leftArm);
		this.rightSleeve.copyTransform(this.rightArm);
		this.jacket.copyTransform(this.body);
		this.hat.copyTransform(this.head);
		this.leftPants.copyTransform(this.leftLeg);
		this.rightPants.copyTransform(this.rightLeg);
	}

	/** 0..1: мавҷи кӯтоҳ дар атрофи тики c (лаҳзаи зарба). */
	private static float swing(float t, float c) {
		return Math.max(0f, 1f - Math.abs(t - c) / 3f);
	}
}
