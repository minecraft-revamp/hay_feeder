package com.hayfeeder.registry;

import com.hayfeeder.HayFeederFabric;
import com.hayfeeder.feature.feeder.HayFeederMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;

public final class ModMenuTypes {
    /**
     * Carries the group size (1..N) from the server to the client when opening
     * the menu. The client uses it to instantiate the right number of dummy
     * food slots; slot states are then synced normally by the menu protocol.
     *
     * Vanilla MC 26.1 swapped the old {@code FriendlyByteBuf}-based menu open
     * payload for a {@link net.minecraft.network.codec.StreamCodec}-driven one.
     * Fabric exposes that via {@link ExtendedMenuType}; NeoForge's
     * {@code IMenuTypeExtension.create((id, inv, buf) -> ...)} is the equivalent.
     */
    public static final MenuType<HayFeederMenu> HAY_FEEDER = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(HayFeederFabric.MOD_ID, "hay_feeder"),
            new ExtendedMenuType<HayFeederMenu, Integer>(
                    (containerId, inv, count) -> new HayFeederMenu(containerId, inv, count),
                    ByteBufCodecs.VAR_INT));

    private ModMenuTypes() {}

    public static void bootstrap() {}
}
