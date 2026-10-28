package cc.sighs.spatialloop.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.Optional;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionStorage.class)
public interface SectionStorageRetirementAccessor {
    @Accessor("storage")
    Long2ObjectMap<Optional<?>> spatialloop$sectionCache();

    @Accessor("dirty")
    LongLinkedOpenHashSet spatialloop$dirtySections();

    @Accessor("simpleRegionStorage")
    SimpleRegionStorage spatialloop$regionStorage();

    @Accessor("levelHeightAccessor")
    LevelHeightAccessor spatialloop$heightAccessor();
}
