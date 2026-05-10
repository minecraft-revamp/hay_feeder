package com.hayfeeder.feature.feeder;

import com.hayfeeder.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HayFeederBlock extends Block implements EntityBlock {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST  = BooleanProperty.create("east");
    public static final BooleanProperty WEST  = BooleanProperty.create("west");
    public static final BooleanProperty DIAG_NE = BooleanProperty.create("diag_ne");
    public static final BooleanProperty DIAG_NW = BooleanProperty.create("diag_nw");
    public static final BooleanProperty DIAG_SE = BooleanProperty.create("diag_se");
    public static final BooleanProperty DIAG_SW = BooleanProperty.create("diag_sw");

    public HayFeederBlock(BlockBehaviour.Properties props) {
        super(props.randomTicks());
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(DIAG_NE, false)
                .setValue(DIAG_NW, false)
                .setValue(DIAG_SE, false)
                .setValue(DIAG_SW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, DIAG_NE, DIAG_NW, DIAG_SE, DIAG_SW);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HayFeederBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        BlockGetter level = ctx.getLevel();
        return defaultBlockState()
                .setValue(NORTH, isFeeder(level, pos.north()))
                .setValue(SOUTH, isFeeder(level, pos.south()))
                .setValue(EAST,  isFeeder(level, pos.east()))
                .setValue(WEST,  isFeeder(level, pos.west()))
                .setValue(DIAG_NE, isFeeder(level, pos.east().north()))
                .setValue(DIAG_NW, isFeeder(level, pos.west().north()))
                .setValue(DIAG_SE, isFeeder(level, pos.east().south()))
                .setValue(DIAG_SW, isFeeder(level, pos.west().south()));
    }

    private boolean isFeeder(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(this);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction directionToNeighbour,
            BlockPos neighbourPos,
            BlockState neighbourState,
            RandomSource random
    ) {
        if (directionToNeighbour.getAxis().isHorizontal()) {
            BooleanProperty prop = propertyFor(directionToNeighbour);
            if (prop != null) {
                return state.setValue(prop, neighbourState.is(this));
            }
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    private static @Nullable BooleanProperty propertyFor(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST  -> EAST;
            case WEST  -> WEST;
            default    -> null;
        };
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
            be.tickFeeding(level, pos, state);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof Container c) {
            return AbstractContainerMenu.getRedstoneSignalFromContainer(c);
        }
        return 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof Container c) {
            return AbstractContainerMenu.getRedstoneSignalFromContainer(c);
        }
        return 0;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return openGroupMenu(level, pos, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return openGroupMenu(level, pos, player);
    }

    private InteractionResult openGroupMenu(Level level, BlockPos pos, Player player) {
        List<HayFeederBlockEntity> group = FeederGroup.findMembers(level, pos, this);
        if (group.isEmpty()) return InteractionResult.PASS;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            // ExtendedMenuProvider lets the server attach the int payload (group size)
            // to the open-screen packet so the client builds the right number of dummy
            // food slots before slot states sync. The Fabric ExtendedMenuType StreamCodec
            // (registered in ModMenuTypes) decodes this on the client side.
            HayFeederExtendedMenuProvider provider = new HayFeederExtendedMenuProvider(group);
            sp.openMenu(provider);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        notifyDiagonals(level, pos, true);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        notifyDiagonals(level, pos, false);
        if (!level.isClientSide() && !player.getAbilities().instabuild) {
            if (level.getBlockEntity(pos) instanceof HayFeederBlockEntity be) {
                ItemStack remaining = be.getContents();
                if (!remaining.isEmpty()) {
                    Block.popResource(level, pos, remaining.copy());
                }
            }
            Block.popResource(level, pos, new ItemStack(ModItems.HAY_FEEDER));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private void notifyDiagonals(Level level, BlockPos myPos, boolean iExist) {
        // For each diagonal of mine, set their corresponding flag to indicate I exist (or not).
        setDiagFlag(level, myPos.east().north(), DIAG_SW, iExist); // I'm SW of them
        setDiagFlag(level, myPos.west().north(), DIAG_SE, iExist); // I'm SE of them
        setDiagFlag(level, myPos.east().south(), DIAG_NW, iExist); // I'm NW of them
        setDiagFlag(level, myPos.west().south(), DIAG_NE, iExist); // I'm NE of them
    }

    private void setDiagFlag(Level level, BlockPos pos, BooleanProperty flag, boolean value) {
        BlockState state = level.getBlockState(pos);
        if (state.is(this) && state.getValue(flag) != value) {
            level.setBlock(pos, state.setValue(flag, value), Block.UPDATE_ALL);
        }
    }

    /**
     * Server-side menu provider that exposes the feeder group size to the
     * client via {@link net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider}.
     * Wraps a vanilla {@link SimpleMenuProvider} for the menu factory + display
     * name so we don't reimplement those pieces.
     */
    private static final class HayFeederExtendedMenuProvider
            implements net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<Integer> {
        private final List<HayFeederBlockEntity> group;
        private final SimpleMenuProvider delegate;

        HayFeederExtendedMenuProvider(List<HayFeederBlockEntity> group) {
            this.group = group;
            this.delegate = new SimpleMenuProvider(
                    (id, inv, p) -> new HayFeederMenu(id, inv, group),
                    Component.translatable("container.hay_feeder.hay_feeder"));
        }

        @Override
        public Integer getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
            return group.size();
        }

        @Override
        public Component getDisplayName() {
            return delegate.getDisplayName();
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId,
                                                net.minecraft.world.entity.player.Inventory inventory,
                                                Player player) {
            return delegate.createMenu(containerId, inventory, player);
        }
    }
}
