package com.hayfeeder.feature.feeder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FeederGroup {
    private FeederGroup() {}

    public static List<HayFeederBlockEntity> findMembers(LevelAccessor level, BlockPos start, Block block) {
        List<HayFeederBlockEntity> members = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!level.getBlockState(pos).is(block)) continue;
            if (!(level.getBlockEntity(pos) instanceof HayFeederBlockEntity be)) continue;
            members.add(be);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(d);
                if (visited.add(next)) queue.add(next);
            }
        }
        return members;
    }
}
