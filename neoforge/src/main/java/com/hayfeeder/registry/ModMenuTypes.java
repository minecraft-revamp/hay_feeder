package com.hayfeeder.registry;

import com.hayfeeder.HayFeeder;
import com.hayfeeder.feature.feeder.HayFeederMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, HayFeeder.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<HayFeederMenu>> HAY_FEEDER =
            MENU_TYPES.register("hay_feeder",
                    () -> IMenuTypeExtension.create(
                            (containerId, inv, data) -> new HayFeederMenu(containerId, inv)));

    private ModMenuTypes() {}
}
