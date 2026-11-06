package com.viltrumite.boss.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;

/** Дуди сиёҳ (аз видеои эффект сохта шудааст): 8 кадр, оҳиста ғайб мешавад. */
public class ViltrumiteSmokeParticle extends SpriteBillboardParticle {
	private final SpriteProvider sprites;

	protected ViltrumiteSmokeParticle(ClientWorld world, double x, double y, double z,
			double vx, double vy, double vz, SpriteProvider sprites) {
		super(world, x, y, z);
		this.sprites = sprites;
		this.maxAge = 28 + this.random.nextInt(12);
		this.scale = 0.9f + this.random.nextFloat() * 0.6f;
		this.velocityX = vx + (this.random.nextDouble() - 0.5) * 0.01;
		this.velocityY = vy + 0.005;
		this.velocityZ = vz + (this.random.nextDouble() - 0.5) * 0.01;
		this.gravityStrength = 0f;
		this.collidesWithWorld = false;
		this.angle = this.random.nextFloat() * 6.2831f;
		this.prevAngle = this.angle;
		this.setSpriteForAge(sprites);
	}

	@Override
	public void tick() {
		super.tick();
		this.setSpriteForAge(this.sprites);
		float life = (float) this.age / (float) this.maxAge;
		this.alpha = Math.max(0f, 1f - life * life);
		this.velocityX *= 0.93;
		this.velocityY *= 0.93;
		this.velocityZ *= 0.93;
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	public static class Factory implements ParticleFactory<DefaultParticleType> {
		private final SpriteProvider sprites;

		public Factory(SpriteProvider sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(DefaultParticleType type, ClientWorld world,
				double x, double y, double z, double vx, double vy, double vz) {
			return new ViltrumiteSmokeParticle(world, x, y, z, vx, vy, vz, sprites);
		}
	}
}
