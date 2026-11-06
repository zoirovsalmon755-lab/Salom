package com.viltrumite.boss;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ViltrumiteMod implements ModInitializer {
	public static final String MOD_ID = "viltrumite_boss";
	public static Item SPAWN_EGG;

	@Override
	public void onInitialize() {
		ModParticles.register();
		ModEntities.register();

		SPAWN_EGG = Registry.register(Registries.ITEM, new Identifier(MOD_ID, "viltrumite_spawn_egg"),
				new SpawnEggItem(ModEntities.VILTRUMITE, 0xFFD400, 0x2B1E00, new FabricItemSettings()));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(SPAWN_EGG));
	}
}
