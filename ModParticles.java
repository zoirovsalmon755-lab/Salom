package com.viltrumite.boss;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModParticles {
	public static final DefaultParticleType VILTRUMITE_SMOKE = Registry.register(
			Registries.PARTICLE_TYPE,
			new Identifier(ViltrumiteMod.MOD_ID, "viltrumite_smoke"),
			FabricParticleTypes.simple());

	public static void register() {
		// loading the class registers the particle
	}
}
