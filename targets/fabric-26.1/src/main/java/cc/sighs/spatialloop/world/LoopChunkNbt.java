package cc.sighs.spatialloop.world;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 区块级存档 NBT 的坐标平移。
 *
 * <p>带绝对坐标的部分必须跟着存档槽一起平移，否则把区块 C+P 的数据读到 C（或反过来）时，
 * 方块实体、计划刻、实体、POI、结构起点/引用会留在原来的周期里。方块本身用区块内相对坐标，不需要处理。
 */
public final class LoopChunkNbt {
    private LoopChunkNbt() {
    }

    /** 区块本体：{@code xPos}/{@code zPos}、方块实体、计划刻、结构起点与引用。 */
    public static void rebase(CompoundTag tag, int deltaChunkX, int deltaChunkZ) {
        if (deltaChunkX == 0 && deltaChunkZ == 0) {
            return;
        }
        int deltaX = deltaChunkX * 16;
        int deltaZ = deltaChunkZ * 16;

        Optional<Integer> posX = tag.getInt("xPos");
        if (posX.isPresent()) {
            tag.putInt("xPos", posX.get() + deltaChunkX);
        }
        Optional<Integer> posZ = tag.getInt("zPos");
        if (posZ.isPresent()) {
            tag.putInt("zPos", posZ.get() + deltaChunkZ);
        }

        ListTag blockEntities = tag.getListOrEmpty("block_entities");
        for (int i = 0; i < blockEntities.size(); i++) {
            Optional<CompoundTag> entry = blockEntities.getCompound(i);
            if (entry.isEmpty()) {
                continue;
            }
            CompoundTag blockEntity = entry.get();
            blockEntity.putInt("x", blockEntity.getIntOr("x", 0) + deltaX);
            blockEntity.putInt("z", blockEntity.getIntOr("z", 0) + deltaZ);
        }

        shiftTicks(tag, "block_ticks", deltaX, deltaZ);
        shiftTicks(tag, "fluid_ticks", deltaX, deltaZ);
        rebaseStructures(tag.getCompoundOrEmpty("structures"), deltaChunkX, deltaChunkZ);
    }

    /**
     * 结构数据：{@code starts.<id>} 的 {@code ChunkX}/{@code ChunkZ} 与每个 piece 的
     * {@code BB}/{@code TPX}/{@code TPZ}/{@code PosX}/{@code PosZ}，以及 {@code References} 里的区块坐标。
     */
    private static void rebaseStructures(CompoundTag structures, int deltaChunkX, int deltaChunkZ) {
        if (structures.isEmpty()) {
            return;
        }
        int deltaX = deltaChunkX * 16;
        int deltaZ = deltaChunkZ * 16;

        CompoundTag starts = structures.getCompoundOrEmpty("starts");
        for (String key : starts.keySet().toArray(String[]::new)) {
            CompoundTag start = starts.getCompoundOrEmpty(key);
            shiftInt(start, "ChunkX", deltaChunkX);
            shiftInt(start, "ChunkZ", deltaChunkZ);
            ListTag children = start.getListOrEmpty("Children");
            for (int i = 0; i < children.size(); i++) {
                Optional<CompoundTag> entry = children.getCompound(i);
                if (entry.isEmpty()) {
                    continue;
                }
                CompoundTag piece = entry.get();
                shiftBox(piece, "BB", deltaX, deltaZ);
                shiftInt(piece, "TPX", deltaX);
                shiftInt(piece, "TPZ", deltaZ);
                shiftInt(piece, "PosX", deltaX);
                shiftInt(piece, "PosZ", deltaZ);
            }
        }

        CompoundTag references = structures.getCompoundOrEmpty("References");
        for (String key : references.keySet().toArray(String[]::new)) {
            Optional<long[]> stored = references.getLongArray(key);
            if (stored.isEmpty()) {
                continue;
            }
            long[] chunks = stored.get();
            for (int i = 0; i < chunks.length; i++) {
                chunks[i] = packChunk(
                        unpackChunkX(chunks[i]) + deltaChunkX,
                        unpackChunkZ(chunks[i]) + deltaChunkZ
                );
            }
            references.putLongArray(key, chunks);
        }
    }

    private static void shiftInt(CompoundTag holder, String key, int delta) {
        Optional<Integer> value = holder.getInt(key);
        if (value.isPresent()) {
            holder.putInt(key, value.get() + delta);
        }
    }

    /** {@code BB} 由 {@code BoundingBox.CODEC}（INT_STREAM）编码，按 INT 列表或 int 数组处理。 */
    private static void shiftBox(CompoundTag holder, String key, int deltaX, int deltaZ) {
        Tag box = holder.get(key);
        if (box instanceof IntArrayTag array && array.size() == 6) {
            int[] values = array.getAsIntArray();
            values[0] += deltaX;
            values[3] += deltaX;
            values[2] += deltaZ;
            values[5] += deltaZ;
            holder.put(key, new IntArrayTag(values));
        } else if (box instanceof ListTag list && list.size() == 6 && isIntList(list)) {
            list.set(0, IntTag.valueOf(list.getIntOr(0, 0) + deltaX));
            list.set(3, IntTag.valueOf(list.getIntOr(3, 0) + deltaX));
            list.set(2, IntTag.valueOf(list.getIntOr(2, 0) + deltaZ));
            list.set(5, IntTag.valueOf(list.getIntOr(5, 0) + deltaZ));
        }
    }

    /** 实体区块：{@code Position}（区块坐标）与每条实体的 {@code Pos}（世界坐标）。 */
    public static void rebaseEntities(CompoundTag tag, int deltaChunkX, int deltaChunkZ) {
        if (deltaChunkX == 0 && deltaChunkZ == 0) {
            return;
        }
        int deltaX = deltaChunkX * 16;
        int deltaZ = deltaChunkZ * 16;

        Optional<int[]> position = tag.getIntArray("Position");
        if (position.isPresent() && position.get().length == 2) {
            int[] value = position.get();
            tag.put("Position", new IntArrayTag(new int[]{value[0] + deltaChunkX, value[1] + deltaChunkZ}));
        }

        ListTag entities = tag.getListOrEmpty("Entities");
        for (int i = 0; i < entities.size(); i++) {
            Optional<CompoundTag> entry = entities.getCompound(i);
            if (entry.isEmpty()) {
                continue;
            }
            ListTag pos = entry.get().getListOrEmpty("Pos");
            if (pos.size() >= 3) {
                shiftDouble(pos, 0, deltaX);
                shiftDouble(pos, 2, deltaZ);
            }
        }
    }

    /** POI 区块：{@code Sections.<Y>.Records[].pos}（BlockPos 按 INT_STREAM 编码成 int 列表）。 */
    public static void rebasePoi(CompoundTag tag, int deltaChunkX, int deltaChunkZ) {
        if (deltaChunkX == 0 && deltaChunkZ == 0) {
            return;
        }
        int deltaX = deltaChunkX * 16;
        int deltaZ = deltaChunkZ * 16;

        CompoundTag sections = tag.getCompoundOrEmpty("Sections");
        for (String key : sections.keySet().toArray(String[]::new)) {
            CompoundTag section = sections.getCompoundOrEmpty(key);
            ListTag records = section.getListOrEmpty("Records");
            for (int i = 0; i < records.size(); i++) {
                Optional<CompoundTag> entry = records.getCompound(i);
                if (entry.isEmpty()) {
                    continue;
                }
                shiftBlockPosTag(entry.get(), "pos", deltaX, deltaZ);
            }
        }
    }

    /** BlockPos 在 NBT 里可能是 int 列表（INT_STREAM）或 int 数组，两种都平移。 */
    private static void shiftBlockPosTag(CompoundTag holder, String key, int deltaX, int deltaZ) {
        Tag pos = holder.get(key);
        if (pos instanceof ListTag list && list.size() >= 3 && isIntList(list)) {
            list.set(0, IntTag.valueOf(list.getIntOr(0, 0) + deltaX));
            list.set(2, IntTag.valueOf(list.getIntOr(2, 0) + deltaZ));
        } else if (pos instanceof IntArrayTag array && array.size() == 3) {
            int[] values = array.getAsIntArray();
            holder.put(key, new IntArrayTag(new int[]{values[0] + deltaX, values[1], values[2] + deltaZ}));
        }
    }

    private static void shiftTicks(CompoundTag tag, String key, int deltaX, int deltaZ) {
        ListTag ticks = tag.getListOrEmpty(key);
        for (int i = 0; i < ticks.size(); i++) {
            Optional<CompoundTag> entry = ticks.getCompound(i);
            if (entry.isEmpty()) {
                continue;
            }
            CompoundTag tick = entry.get();
            // SavedTick 的 i 是注册 ID，坐标分别存放在 x/y/z 中。
            shiftInt(tick, "x", deltaX);
            shiftInt(tick, "z", deltaZ);
        }
    }

    private static void shiftDouble(ListTag list, int index, double delta) {
        Optional<Double> value = list.getDouble(index);
        if (value.isPresent()) {
            list.set(index, DoubleTag.valueOf(value.get() + delta));
        }
    }

    /**
     * 26.1 的 {@link ListTag} 不再暴露元素的 {@code TagType}（{@code getElementType()} 已移除），
     * 改用逐个元素判定，保持原先「只处理 INT 列表」的语义。
     */
    private static boolean isIntList(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof IntTag)) {
                return false;
            }
        }
        return true;
    }

    // 不用 ChunkPos.pack/getX/getZ：26.1 的 ChunkPos 静态初始化会拉起 ChunkPyramid/注册表，
    // 单元测试没有 FML 环境时无法 bootstrap。位运算与 ChunkPos 完全一致。
    private static long packChunk(int x, int z) {
        return x & 4294967295L | (z & 4294967295L) << 32;
    }

    private static int unpackChunkX(long packed) {
        return (int) (packed & 4294967295L);
    }

    private static int unpackChunkZ(long packed) {
        return (int) (packed >>> 32 & 4294967295L);
    }
}
