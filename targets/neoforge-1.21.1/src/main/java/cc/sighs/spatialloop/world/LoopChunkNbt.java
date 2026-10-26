package cc.sighs.spatialloop.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

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
        rebaseStructures(tag.getCompound("structures"), deltaChunkX, deltaChunkZ);
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

        CompoundTag starts = structures.getCompound("starts");
        for (String key : starts.getAllKeys().toArray(String[]::new)) {
            CompoundTag start = starts.getCompound(key);
            shiftInt(start, "ChunkX", deltaChunkX);
            shiftInt(start, "ChunkZ", deltaChunkZ);
            ListTag children = start.getList("Children", Tag.TAG_COMPOUND);
            for (int i = 0; i < children.size(); i++) {
                CompoundTag piece = children.getCompound(i);
                shiftBox(piece, "BB", deltaX, deltaZ);
                shiftInt(piece, "TPX", deltaX);
                shiftInt(piece, "TPZ", deltaZ);
                shiftInt(piece, "PosX", deltaX);
                shiftInt(piece, "PosZ", deltaZ);
            }
        }

        CompoundTag references = structures.getCompound("References");
        for (String key : references.getAllKeys().toArray(String[]::new)) {
            long[] chunks = references.getLongArray(key);
            for (int i = 0; i < chunks.length; i++) {
                chunks[i] = ChunkPos.asLong(
                        ChunkPos.getX(chunks[i]) + deltaChunkX,
                        ChunkPos.getZ(chunks[i]) + deltaChunkZ
                );
            }
            references.putLongArray(key, chunks);
        }
    }

    private static void shiftInt(CompoundTag holder, String key, int delta) {
        if (holder.contains(key, Tag.TAG_INT)) {
            holder.putInt(key, holder.getInt(key) + delta);
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
        } else if (box instanceof ListTag list && list.size() == 6 && list.getElementType() == Tag.TAG_INT) {
            list.set(0, IntTag.valueOf(list.getInt(0) + deltaX));
            list.set(3, IntTag.valueOf(list.getInt(3) + deltaX));
            list.set(2, IntTag.valueOf(list.getInt(2) + deltaZ));
            list.set(5, IntTag.valueOf(list.getInt(5) + deltaZ));
        }
    }

    /** 实体区块：{@code Position}（区块坐标）与每条实体的 {@code Pos}（世界坐标）。 */
    public static void rebaseEntities(CompoundTag tag, int deltaChunkX, int deltaChunkZ) {
        if (deltaChunkX == 0 && deltaChunkZ == 0) {
            return;
        }
        int deltaX = deltaChunkX * 16;
        int deltaZ = deltaChunkZ * 16;

        int[] position = tag.getIntArray("Position");
        if (position.length == 2) {
            tag.put("Position", new IntArrayTag(new int[]{position[0] + deltaChunkX, position[1] + deltaChunkZ}));
        }

        ListTag entities = tag.getList("Entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entity = entities.getCompound(i);
            ListTag pos = entity.getList("Pos", Tag.TAG_DOUBLE);
            if (pos.size() >= 3) {
                pos.set(0, DoubleTag.valueOf(pos.getDouble(0) + deltaX));
                pos.set(2, DoubleTag.valueOf(pos.getDouble(2) + deltaZ));
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

        CompoundTag sections = tag.getCompound("Sections");
        for (String key : sections.getAllKeys().toArray(String[]::new)) {
            CompoundTag section = sections.getCompound(key);
            ListTag records = section.getList("Records", Tag.TAG_COMPOUND);
            for (int i = 0; i < records.size(); i++) {
                CompoundTag record = records.getCompound(i);
                shiftBlockPosTag(record, "pos", deltaX, deltaZ);
            }
        }
    }

    /** BlockPos 在 NBT 里可能是 int 列表（INT_STREAM）或 int 数组，两种都平移。 */
    private static void shiftBlockPosTag(CompoundTag holder, String key, int deltaX, int deltaZ) {
        Tag pos = holder.get(key);
        if (pos instanceof ListTag list && list.size() >= 3 && list.getElementType() == Tag.TAG_INT) {
            list.set(0, IntTag.valueOf(list.getInt(0) + deltaX));
            list.set(2, IntTag.valueOf(list.getInt(2) + deltaZ));
        } else if (pos instanceof IntArrayTag array && array.size() == 3) {
            int[] values = array.getAsIntArray();
            holder.put(key, new IntArrayTag(new int[]{values[0] + deltaX, values[1], values[2] + deltaZ}));
        }
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
