package com.hayfeeder.client;

import com.hayfeeder.feature.feeder.FoodTints;
import com.hayfeeder.feature.feeder.HayFeederBlockEntity;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tint source for the hay_feeder block's contents element. The model JSON marks the
 * inner cube faces with {@code "tintindex": 0}; this class is registered as the
 * layer-0 tint source on the block, so MC will multiply the hay-block texture pixels
 * by the int we return here.
 *
 * <p>MC 26.1 renamed {@code BlockColor} to {@code BlockTintSource}. The interface no
 * longer takes a {@code tintIndex} (the layer is selected from the registered List
 * before this is called), and {@code BlockAndTintGetter} moved from
 * {@code net.minecraft.world.level} to {@code net.minecraft.client.renderer.block}.
 */
public class HayFeederBlockColor implements BlockTintSource {

    private static final int DEFAULT = 0xFFFFFFFF;

    @Override
    public int color(BlockState state) {
        // Item-form / no-world fallback (e.g. inventory rendering picks this up).
        return DEFAULT;
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (level == null || pos == null) return DEFAULT;
        if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
            ItemStack contents = be.getContents();
            if (!contents.isEmpty()) {
                return FoodTints.colorFor(contents.getItem());
            }
        }
        return DEFAULT;
    }
}
