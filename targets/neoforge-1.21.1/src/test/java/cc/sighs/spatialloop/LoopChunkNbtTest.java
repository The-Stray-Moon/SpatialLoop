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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.TickPriority;
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

        SavedTick<String> tick = new SavedTick<>(
                "minecraft:water", new BlockPos(1607, -60, 5), 13, TickPriority.HIGH);
        ListTag ticks = new ListTag();
        ticks.add(tick.save(id -> id));
        tag.put("fluid_ticks", ticks);

        LoopChunkNbt.rebase(tag, -96, 0);

        assertEquals(4, tag.getInt("xPos"));
        assertEquals(-3, tag.getInt("zPos"));
        CompoundTag movedChest = tag.getList("block_entities", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(69, movedChest.getInt("x"));
        assertEquals(5, movedChest.getInt("z"));
        CompoundTag movedTick = tag.getList("fluid_ticks", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals("minecraft:water", movedTick.getString("i"));
        assertEquals(new SavedTick<>("minecraft:water", new BlockPos(71, -60, 5), 13, TickPriority.HIGH),
                SavedTick.loadTick(movedTick, java.util.Optional::of).orElseThrow());
    }

    @Test
    void realSavedTicksSurviveBothAxisTranslationAndRoundTrip() {
        for (String key : new String[]{"block_ticks", "fluid_ticks"}) {
            String id = key.equals("block_ticks") ? "minecraft:repeater" : "minecraft:water";
            SavedTick<String> tick = new SavedTick<>(id, new BlockPos(-17, 230, 1041), 41, TickPriority.LOW);
            CompoundTag tag = new CompoundTag();
            ListTag ticks = new ListTag();
            ticks.add(tick.save(value -> value));
            tag.put(key, ticks);
            CompoundTag original = tag.copy();

            LoopChunkNbt.rebase(tag, 32, -64);

            CompoundTag moved = tag.getList(key, Tag.TAG_COMPOUND).getCompound(0);
            assertEquals(new SavedTick<>(id, new BlockPos(495, 230, 17), 41, TickPriority.LOW),
                    SavedTick.loadTick(moved, java.util.Optional::of).orElseThrow());
            java.util.List<SavedTick<String>> loaded = new java.util.ArrayList<>();
            SavedTick.loadTickList(tag.getList(key, Tag.TAG_COMPOUND), java.util.Optional::of,
                    new ChunkPos(30, 1), loaded::add);
            assertEquals(1, loaded.size());

            LoopChunkNbt.rebase(tag, -32, 64);
            assertEquals(original, tag);
        }
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
        references.putLongArray("minecraft:village_plains", new long[]{ChunkPos.asLong(100, 0)});
        structures.put("References", references);
        tag.put("structures", structures);

        LoopChunkNbt.rebase(tag, -96, 0);

        CompoundTag moved = tag.getCompound("structures")
                .getCompound("starts")
                .getCompound("minecraft:village_plains");
        assertEquals(4, moved.getInt("ChunkX"));
        assertEquals(0, moved.getInt("ChunkZ"));

        CompoundTag movedPiece = moved.getList("Children", Tag.TAG_COMPOUND).getCompound(0);
        ListTag movedBox = movedPiece.getList("BB", Tag.TAG_INT);
        assertEquals(64, movedBox.getInt(0));
        assertEquals(0, movedBox.getInt(2));
        assertEquals(79, movedBox.getInt(3));
        assertEquals(15, movedBox.getInt(5));
        assertEquals(64, movedPiece.getInt("TPX"));
        assertEquals(64, movedPiece.getInt("PosX"));

        assertEquals(
                ChunkPos.asLong(4, 0),
                tag.getCompound("structures").getCompound("References").getLongArray("minecraft:village_plains")[0]
        );
    }
}
