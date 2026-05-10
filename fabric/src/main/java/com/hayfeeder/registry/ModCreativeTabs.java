package com.hayfeeder.registry;

import com.hayfeeder.HayFeederFabric;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;

public final class ModCreativeTabs {
    public static final CreativeModeTab MAIN_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(HayFeederFabric.MOD_ID, "main"),
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 7)
                    .title(Component.translatable("itemGroup.hay_feeder.main"))
                    .icon(() -> ModItems.HAY_FEEDER.getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.HAY_FEEDER);
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void bootstrap() {}
}
