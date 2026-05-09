package com.hayfeeder.feature.feeder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class HayFeederBlock extends Block implements EntityBlock {
    public static final int MAX_FEEDS = 8;
    public static final IntegerProperty FEEDS_LEFT = IntegerProperty.create("feeds_left", 0, MAX_FEEDS);

    public HayFeederBlock(BlockBehaviour.Properties props) {
        super(props.randomTicks());
        registerDefaultState(stateDefinition.any().setValue(FEEDS_LEFT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FEEDS_LEFT);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HayFeederBlockEntity(pos, state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
            be.tickFeeding(level, pos, state);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof HayFeederBlockEntity be)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        int absorbed = be.tryRefill(stack);
        if (absorbed == 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!player.getAbilities().instabuild) {
            stack.shrink(absorbed);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.getAbilities().instabuild) {
            if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
                ItemStack remaining = be.getContents();
                if (!remaining.isEmpty()) {
                    Block.popResource(level, pos, remaining.copy());
                }
            }
            Block.popResource(level, pos, new ItemStack(Items.HAY_BLOCK));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
