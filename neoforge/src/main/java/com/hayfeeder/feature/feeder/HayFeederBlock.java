package com.hayfeeder.feature.feeder;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class HayFeederBlock extends Block {
    public static final int MAX_FEEDS = 8;
    public static final IntegerProperty FEEDS_LEFT = IntegerProperty.create("feeds_left", 0, MAX_FEEDS);

    public HayFeederBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FEEDS_LEFT, MAX_FEEDS));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FEEDS_LEFT);
    }
}
