package cc.sighs.spatialloop.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 区块 NBT 的坐标平移。
 *
 * <p>区块数据里带绝对坐标的部分必须跟着存档槽一起平移，否则把区块 C+P 的数据读到 C（或反过来）
 * 时，方块实体、计划刻会留在原来的周期里。方块本身用的是区块内相对坐标，不需要处理。
 *
 * <p>结构起点/引用也带区块坐标，但本生成器不生成结构（{@code createStructures} 是空实现），
 * 这些标签始终为空；将来若支持结构，必须在这里补上平移。
 */
public final class LoopChunkNbt {
    private LoopChunkNbt() {
    }

    public static void rebase(CompoundTag tag, int deltaChunkX, int deltaChunkZ) {
        if (deltaChunkX == 0 && deltaChunkZ == 0) {
            return;
        }
        int deltaX = deltaChunkX * 16;
        int deltaZ = deltaChunkZ * 16;

        if (tag.contains("xPos", Tag.TAG_INT)) {
            tag.putInt("xPos", tag.getInt("xPos") + deltaChunkX);
        }
        if (tag.contains("zPos", Tag.TAG_INT)) {
            tag.putInt("zPos", tag.getInt("zPos") + deltaChunkZ);
        }

        ListTag blockEntities = tag.getList("block_entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockEntities.size(); i++) {
            CompoundTag blockEntity = blockEntities.getCompound(i);
            blockEntity.putInt("x", blockEntity.getInt("x") + deltaX);
            blockEntity.putInt("z", blockEntity.getInt("z") + deltaZ);
        }

        shiftTicks(tag, "block_ticks", deltaX, deltaZ);
        shiftTicks(tag, "fluid_ticks", deltaX, deltaZ);
    }

    private static void shiftTicks(CompoundTag tag, String key, int deltaX, int deltaZ) {
        ListTag ticks = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < ticks.size(); i++) {
            CompoundTag tick = ticks.getCompound(i);
            BlockPos pos = BlockPos.of(tick.getLong("i"));
            tick.putLong("i", BlockPos.asLong(pos.getX() + deltaX, pos.getY(), pos.getZ() + deltaZ));
        }
    }
}
