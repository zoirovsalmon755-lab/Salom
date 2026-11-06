package com.viltrumite.boss.client;

import com.viltrumite.boss.ModEntities;
import com.viltrumite.boss.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class ViltrumiteClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.VILTRUMITE, ViltrumiteRenderer::new);
		ParticleFactoryRegistry.getInstance().register(ModParticles.VILTRUMITE_SMOKE, ViltrumiteSmokeParticle.Factory::new);
	}
}
