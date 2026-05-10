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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class HayFeederBlock extends Block implements EntityBlock {
    public static final EnumProperty<FillStage> FILL_STAGE = EnumProperty.create("fill_stage", FillStage.class);

    public HayFeederBlock(BlockBehaviour.Properties props) {
        super(props.randomTicks());
        registerDefaultState(stateDefinition.any().setValue(FILL_STAGE, FillStage.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL_STAGE);
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
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
            player.openMenu(be, pos);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
            player.openMenu(be, pos);
        }
        return InteractionResult.SUCCESS_SERVER;
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
