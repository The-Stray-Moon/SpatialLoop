package cc.sighs.spatialloop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.sighs.spatialloop.client.SpatialLoopShaderPatch;
import org.junit.jupiter.api.Test;

/** 26.1 的 shader 是纯 GLSL：半径烘成常量，位置改写用投影矩阵排除 GUI/正交渲染。 */
class SpatialLoopShaderPatchTest {
    private static final String WORLD = """
            #version 150

            in vec3 Position;

            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
            }
            """;

    @Test
    void worldShadersAreBentWithAPerspectiveGuard() {
        String patched = SpatialLoopShaderPatch.patchVertex("entity", WORLD);
        assertTrue(patched.contains("const float spatialloop_curve_radius"));
        assertTrue(patched.contains("vec3 spatialloop_bend(vec3 pos)"));
        assertTrue(patched.contains("ProjMat[3][3] == 0.0"));
        assertTrue(patched.contains("spatialloop_bend(Position)"));
        assertFalse(patched.contains("gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);"));
        assertEquals(patched, SpatialLoopShaderPatch.patchVertex("entity", patched));
    }

    @Test
    void localPositionShadersUseTheirOwnPosVariable() {
        String terrain = """
                #version 150
                in vec3 Position;
                void main() {
                    vec3 pos = Position + ModelOffset;
                    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
                }
                """;
        String patched = SpatialLoopShaderPatch.patchVertex("terrain", terrain);
        assertTrue(patched.contains("spatialloop_bend(pos)"));
        assertTrue(patched.contains("ProjMat[3][3] == 0.0"));
    }

    @Test
    void lineShadersBendBothEndpoints() {
        String lines = """
                #version 150
                void main() {
                    vec4 linePosStart = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position, 1.0);
                    vec4 linePosEnd = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position + Normal, 1.0);
                    gl_Position = vec4(0.0);
                }
                """;
        String patched = SpatialLoopShaderPatch.patchVertex("rendertype_lines", lines);
        assertTrue(patched.contains("spatialloop_bend(Position)"));
        assertTrue(patched.contains("spatialloop_bend(Position + Normal)"));
        assertTrue(patched.contains("ProjMat[3][3] == 0.0"));
    }

    @Test
    void nonWorldShadersAreLeftAlone() {
        assertEquals(WORLD, SpatialLoopShaderPatch.patchVertex("gui", WORLD));
        assertFalse(SpatialLoopShaderPatch.isWorldShader("gui"));
        assertFalse(SpatialLoopShaderPatch.isWorldShader("sky"));
        assertTrue(SpatialLoopShaderPatch.isWorldShader("entity"));
        assertTrue(SpatialLoopShaderPatch.isWorldShader("terrain"));
    }

    @Test
    void shaderIdsAreResolvedOnlyForMinecraftCoreShaders() {
        assertEquals("entity", SpatialLoopShaderPatch.shaderName("minecraft", "core/entity"));
        assertEquals("rendertype_lines", SpatialLoopShaderPatch.shaderName("minecraft", "core/rendertype_lines"));
        assertNull(SpatialLoopShaderPatch.shaderName("othermod", "core/entity"));
        assertNull(SpatialLoopShaderPatch.shaderName("minecraft", "include/entity"));
    }
}
