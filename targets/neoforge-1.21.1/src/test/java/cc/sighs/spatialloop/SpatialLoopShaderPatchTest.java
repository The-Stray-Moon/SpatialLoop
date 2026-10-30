package cc.sighs.spatialloop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.sighs.spatialloop.client.SpatialLoopShaderPatch;
import org.junit.jupiter.api.Test;

class SpatialLoopShaderPatchTest {
    private static final String WORLD = """
            #version 150

            in vec3 Position;

            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
            }
            """;

    @Test
    void everyPatchedShaderKeepsGuiUntouchedWithAPerspectiveGuard() {
        String patched = SpatialLoopShaderPatch.patchVertex("rendertype_entity_solid", WORLD);
        assertTrue(patched.contains("uniform mat4 ViewRot;"));
        assertTrue(patched.contains("uniform float CurveRadius;"));
        assertTrue(patched.contains("spatialloop_bend("));
        // GUI 物品也用 entity shader 渲染，正交投影下必须走回原版路径。
        assertTrue(patched.contains("ProjMat[3][3] == 0.0"));
        assertTrue(patched.contains("gl_Position = ProjMat * sl_view;"));
        assertFalse(patched.contains("gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);"));
    }

    @Test
    void lineShadersArePatchedWithTheSameGuard() {
        String lines = """
                #version 150
                void main() {
                    vec4 linePosStart = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position, 1.0);
                    vec4 linePosEnd = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position + Normal, 1.0);
                    gl_Position = vec4(0.0);
                }
                """;
        String patched = SpatialLoopShaderPatch.patchVertex("rendertype_lines", lines);
        assertTrue(patched.contains("ProjMat[3][3] == 0.0"));
        assertTrue(patched.contains("spatialloop_bend(sl_start_rel)"));
        assertFalse(patched.contains("VIEW_SCALE * ModelViewMat * vec4(Position, 1.0)"));
    }

    @Test
    void nonWorldShadersAreLeftAlone() {
        assertEquals(WORLD, SpatialLoopShaderPatch.patchVertex("rendertype_gui", WORLD));
        assertFalse(SpatialLoopShaderPatch.isWorldShader("rendertype_gui"));
        assertTrue(SpatialLoopShaderPatch.isWorldShader("rendertype_entity_solid"));
        assertTrue(SpatialLoopShaderPatch.isWorldShader("rendertype_text"));
    }

    @Test
    void jsonGetsBothUniformsAndKeepsExistingOnes() {
        String json = """
                {"vertex":"x","fragment":"x","uniforms":[
                  {"name":"ProjMat","type":"matrix4x4","count":16,"values":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]}
                ]}
                """;
        String patched = SpatialLoopShaderPatch.patchJson(json);
        assertTrue(patched.contains("\"ViewRot\""));
        assertTrue(patched.contains("\"CurveRadius\""));
        assertTrue(patched.contains("\"ProjMat\""));
        assertEquals(patched, SpatialLoopShaderPatch.patchJson(patched));
    }
}
