# Spatial Loop

Have you ever wondered what Minecraft would be like if the world were spherical? Given its block-based sandbox setup, that seems rather unlikely.

Even so, an approximately spherical world is possible. At its core, it only requires a looping space.

The idea shows up in fiction too: the classic "ghost wall" phenomenon, or the maze-like formations in xianxia stories — both are examples of spatial loops.

This mod adds a world-generation option that defines the size of the looping area, along with client-side settings for a spherical world.

## What it does

The spatial loop in this mod means: the content is finite, but the space has no boundary. You can walk forever in one direction, yet the scenery and creatures repeat periodically — you are actually passing through the same region again and again.

The design idea comes from a simple thought: imagine a jigsaw puzzle piece whose top and bottom edges match, and whose left and right edges also match. Countless copies of that piece can tile into a finite, endless, continuous plane.

To achieve this, the mod reworks world generation. When creating a world, you set the spatial-loop chunk radius. That setting makes the world-generation noise repeat periodically; together with other changes, a single "puzzle piece" whose terrain and biomes match across both axes is complete.

But this does not mean the world is made of infinitely repeated space.

In practice, with this mod, no matter how far you travel — even to six-digit x/z coordinates — the save file barely grows, and terrain loads surprisingly fast. The reason is simple: however many times it repeats, it is always the same space.

The mod also reworks server-side chunk loading. Chunk data reads are resolved by taking the modulo of the chunk coordinates; the chunk data sent to players is always periodically repeated, and so are entities and other NBT data stored on chunks.

In other words, if player A sees a pig a hundred thousand blocks away, it is the same pig player B sees. If player B turns around, they can even see player A, despite the coordinates being a hundred thousand blocks apart.

**So you can keep walking forward, but you will never leave this space. Go out through the north and come back in through the south — there is no boundary, and nothing to tell you where the loop happens, because terrain, chunks, ores, and cave structures are all continuous.**

**It is also useful for tech modpacks that tone down exploration but still have players addicted to mining: this mod lets you limit basic resources.**

## Spherical world

A true spherical world may still be a long way off, but spatial looping plus a chunk-bending visual effect can approximate one.

The mod provides a fairly flexible client config: bending mode, bending strength, and whether the strength adapts to the spatial-loop chunk radius.

Bending mode means the inner-surface and outer-surface modes; the two screenshots make it obvious.

![Inner-surface mode](https://resource-api.xyeidc.com//client/members/pics/e53dc03c)

![Outer-surface mode](https://resource-api.xyeidc.com//client/members/pics/24cbfe8d)

**Note: this visual effect may not suit everyone. A static screenshot may look fine, but it can cause dizziness in game. Consider carefully whether to enable it.**

## Compatibility

For view distance, the mod follows a "a chunk must never exist in two loop copies at once" design. To ensure old chunks are unloaded before new ones load, the player's view distance is forced into a safe range.

As a result, mods that change view distance, and mods that add extra view distance such as Distant Horizons and Voxy, are inherently incompatible.

For terrain, Tectonic has been tested and is fully compatible. In general, any mod that does not aggressively rewrite vanilla terrain generation should be fine.

For biomes, Biomes O' Plenty and its dependency TerraBlender are compatible, though there may be slightly harsh transitions at boundaries; unless viewed from above, it does not affect the experience much.
