package cc.sighs.spatialloop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cc.sighs.spatialloop.world.LoopChunkNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** 存档 NBT 的坐标平移：格式错了就会把方块实体/实体/POI 留在别的周期里。 */
class LoopChunkNbtTest {
    @Test
    void chunkBlockEntitiesAndTicksMoveWithTheSlot() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("xPos", 100);
        tag.putInt("zPos", -3);

        CompoundTag chest = new CompoundTag();
        chest.putInt("x", 1605);
        chest.putInt("y", -60);
        chest.putInt("z", 5);
        ListTag blockEntities = new ListTag();
        blockEntities.add(chest);
        tag.put("block_entities", blockEntities);

        CompoundTag tick = new CompoundTag();
        tick.putLong("i", BlockPos.asLong(1607, -60, 5));
        tick.putString("i_type", "minecraft:water");
        ListTag ticks = new ListTag();
        ticks.add(tick);
        tag.put("fluid_ticks", ticks);

        LoopChunkNbt.rebase(tag, -96, 0);

        assertEquals(4, tag.getInt("xPos"));
        assertEquals(-3, tag.getInt("zPos"));
        CompoundTag movedChest = tag.getList("block_entities", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(69, movedChest.getInt("x"));
        assertEquals(5, movedChest.getInt("z"));
        assertEquals(
                BlockPos.asLong(71, -60, 5),
                tag.getList("fluid_ticks", Tag.TAG_COMPOUND).getCompound(0).getLong("i")
        );
    }

    @Test
    void entityChunkPositionAndEntityPosMoveTogether() {
        CompoundTag tag = new CompoundTag();
        tag.put("Position", new IntArrayTag(new int[]{100, 0}));

        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(1605.5));
        pos.add(DoubleTag.valueOf(-60.0));
        pos.add(DoubleTag.valueOf(5.5));
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:armor_stand");
        entity.put("Pos", pos);
        ListTag entities = new ListTag();
        entities.add(entity);
        tag.put("Entities", entities);

        LoopChunkNbt.rebaseEntities(tag, -96, 0);

        assertEquals(4, tag.getIntArray("Position")[0]);
        assertEquals(0, tag.getIntArray("Position")[1]);
        ListTag movedPos = tag.getList("Entities", Tag.TAG_COMPOUND).getCompound(0).getList("Pos", Tag.TAG_DOUBLE);
        assertEquals(69.5, movedPos.getDouble(0));
        assertEquals(5.5, movedPos.getDouble(2));
    }

    @Test
    void poiRecordPositionsAreIntStreams() {
        CompoundTag tag = new CompoundTag();
        CompoundTag sections = new CompoundTag();
        CompoundTag section = new CompoundTag();
        ListTag records = new ListTag();
        CompoundTag record = new CompoundTag();

        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(1607));
        pos.add(IntTag.valueOf(-60));
        pos.add(IntTag.valueOf(5));
        record.put("pos", pos);
        record.putString("type", "minecraft:farmer");
        records.add(record);
        section.put("Records", records);
        sections.put("-4", section);
        tag.put("Sections", sections);

        LoopChunkNbt.rebasePoi(tag, -96, 0);

        ListTag moved = tag.getCompound("Sections")
                .getCompound("-4")
                .getList("Records", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getList("pos", Tag.TAG_INT);
        assertEquals(71, moved.getInt(0));
        assertEquals(-60, moved.getInt(1));
        assertEquals(5, moved.getInt(2));
    }
}
