package cc.sighs.spatialloop.world;

import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Computes the exact generation cache radius used for a requested status. */
public final class LoopGenerationReach {
    private LoopGenerationReach() {
    }

    public static int forStatus(ChunkStatus status) {
        return ChunkPyramid.GENERATION_PYRAMID.getStepTo(status).getAccumulatedRadiusOf(ChunkStatus.EMPTY);
    }

    public static int squareDependencyCount(int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Generation dependency radius must not be negative: " + radius);
        }
        if (radius == 0) {
            return 1;
        }
        int side = Math.addExact(Math.multiplyExact(radius, 2), 1);
        return Math.multiplyExact(side, side);
    }
}
