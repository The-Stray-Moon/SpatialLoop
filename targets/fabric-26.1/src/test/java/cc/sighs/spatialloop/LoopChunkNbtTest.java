package cc.sighs.spatialloop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cc.sighs.spatialloop.world.LoopChunkNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/** 存档 NBT 的坐标平移：格式错了就会把方块实体/实体/POI/结构留在别的周期里。 */
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
        tick.putString("i", "minecraft:water");
        tick.putInt("x", 1607);
        tick.putInt("y", -60);
        tick.putInt("z", 5);
        ListTag ticks = new ListTag();
        ticks.add(tick);
        tag.put("fluid_ticks", ticks);

        LoopChunkNbt.rebase(tag, -96, 0);

        assertEquals(4, tag.getIntOr("xPos", 0));
        assertEquals(-3, tag.getIntOr("zPos", 0));
        CompoundTag movedChest = tag.getListOrEmpty("block_entities").getCompound(0).orElseThrow();
        assertEquals(69, movedChest.getIntOr("x", 0));
        assertEquals(5, movedChest.getIntOr("z", 0));
        CompoundTag movedTick = tag.getListOrEmpty("fluid_ticks").getCompound(0).orElseThrow();
        assertEquals("minecraft:water", movedTick.getStringOr("i", ""));
        assertEquals(71, movedTick.getIntOr("x", 0));
        assertEquals(5, movedTick.getIntOr("z", 0));
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

        int[] position = tag.getIntArray("Position").orElseThrow();
        assertEquals(4, position[0]);
        assertEquals(0, position[1]);
        ListTag movedPos = tag.getListOrEmpty("Entities")
                .getCompound(0)
                .orElseThrow()
                .getListOrEmpty("Pos");
        assertEquals(69.5, movedPos.getDouble(0).orElseThrow());
        assertEquals(5.5, movedPos.getDouble(2).orElseThrow());
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

        ListTag moved = tag.getCompoundOrEmpty("Sections")
                .getCompoundOrEmpty("-4")
                .getListOrEmpty("Records")
                .getCompound(0)
                .orElseThrow()
                .getListOrEmpty("pos");
        assertEquals(71, moved.getInt(0).orElseThrow());
        assertEquals(-60, moved.getInt(1).orElseThrow());
        assertEquals(5, moved.getInt(2).orElseThrow());
    }

    @Test
    void structureStartsAndReferencesMoveWithTheSlot() {
        CompoundTag tag = new CompoundTag();
        CompoundTag structures = new CompoundTag();

        CompoundTag start = new CompoundTag();
        start.putInt("ChunkX", 100);
        start.putInt("ChunkZ", 0);
        CompoundTag piece = new CompoundTag();
        ListTag box = new ListTag();
        for (int value : new int[]{1600, -60, 0, 1615, -40, 15}) {
            box.add(IntTag.valueOf(value));
        }
        piece.put("BB", box);
        piece.putInt("TPX", 1600);
        piece.putInt("TPZ", 0);
        piece.putInt("PosX", 1600);
        piece.putInt("PosZ", 0);
        ListTag children = new ListTag();
        children.add(piece);
        start.put("Children", children);

        CompoundTag starts = new CompoundTag();
        starts.put("minecraft:village_plains", start);
        structures.put("starts", starts);

        CompoundTag references = new CompoundTag();
        references.putLongArray("minecraft:village_plains", new long[]{100L});
        structures.put("References", references);
        tag.put("structures", structures);

        LoopChunkNbt.rebase(tag, -96, 0);

        CompoundTag moved = tag.getCompoundOrEmpty("structures")
                .getCompoundOrEmpty("starts")
                .getCompoundOrEmpty("minecraft:village_plains");
        assertEquals(4, moved.getIntOr("ChunkX", 0));
        assertEquals(0, moved.getIntOr("ChunkZ", 0));

        CompoundTag movedPiece = moved.getListOrEmpty("Children").getCompound(0).orElseThrow();
        ListTag movedBox = movedPiece.getListOrEmpty("BB");
        assertEquals(64, movedBox.getInt(0).orElseThrow());
        assertEquals(0, movedBox.getInt(2).orElseThrow());
        assertEquals(79, movedBox.getInt(3).orElseThrow());
        assertEquals(15, movedBox.getInt(5).orElseThrow());
        assertEquals(64, movedPiece.getIntOr("TPX", 0));
        assertEquals(64, movedPiece.getIntOr("PosX", 0));

        assertEquals(
                4L,
                tag.getCompoundOrEmpty("structures")
                        .getCompoundOrEmpty("References")
                        .getLongArray("minecraft:village_plains")
                        .orElseThrow()[0]
        );
    }
}
