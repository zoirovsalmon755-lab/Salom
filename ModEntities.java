package com.viltrumite.boss;

import com.viltrumite.boss.entity.ViltrumiteEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {
	public static final EntityType<ViltrumiteEntity> VILTRUMITE = Registry.register(
			Registries.ENTITY_TYPE,
			new Identifier(ViltrumiteMod.MOD_ID, "viltrumite"),
			FabricEntityTypeBuilder.<ViltrumiteEntity>create(SpawnGroup.MONSTER, ViltrumiteEntity::new)
					.dimensions(EntityDimensions.fixed(0.7f, 1.95f))
					.trackRangeBlocks(128)
					.trackedUpdateRate(1)   // smooth movement at high speed
					.fireImmune()
					.build());

	public static void register() {
		FabricDefaultAttributeRegistry.register(VILTRUMITE, ViltrumiteEntity.createAttributes());
	}
}
