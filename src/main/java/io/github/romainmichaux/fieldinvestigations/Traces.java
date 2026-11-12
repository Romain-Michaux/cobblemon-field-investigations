package io.github.romainmichaux.fieldinvestigations;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

// Places traces in the world: on the surface, on walls, in treetops, underwater, underground or buried.
final class Traces {
    private Traces() {}

    // True while the mod itself replaces disturbed soil (uncovering, cleanup): that is not the player destroying it.
    private static boolean replacingSoil;

    // Where to place the trace: on the ground by default, but scratches go on tree trunks (or a wall), some deposits on rock
    // walls, and every other nest at the top of a tree. The choice is fixed for a given trace.
    static void placeTrace(ServerLevel level, CaseFile d, Trace t) {
        int bx = (int) Math.floor(t.x), bz = (int) Math.floor(t.z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return;
        if (t.placed) {
            BlockState existing = level.getBlockState(new BlockPos(t.px, t.py, t.pz));
            if (existing.is(ModContent.TRACE) || (t.buried && !t.uncovered && existing.is(ModContent.DISTURBED_SOIL))) return;
        }
        if (t.buried && !t.uncovered) {
            if (bury(level, t, bx, bz)) return;
            t.buried = false; // no loose soil around here: the clue sits on the surface like an ordinary trace
        }
        if (t.buried) {
            // Already uncovered but gone (water, falling sand...): put it back in its hole.
            BlockPos hole = new BlockPos(t.px, t.py, t.pz);
            if (level.getBlockState(hole).canBeReplaced()) placeAt(level, t, hole, Direction.UP);
            return;
        }
        if (Engine.is(d, "aquatic")) {
            if (!underwater(level, t, bx, bz)) elsewhere(d, t);
            return;
        }
        if (Engine.is(d, "underground") && t.failures < 6) {
            BlockPos ground = cave(level, bx, bz, 10);
            if (ground != null) {
                placeAt(level, t, ground, Direction.UP);
            } else {
                // No cave below this spot: try elsewhere in the zone. After six attempts the trace ends up on the surface.
                t.failures++;
                elsewhere(d, t);
            }
            return;
        }
        if (t.cave) {
            BlockPos ground = openCave(level, bx, bz);
            if (ground != null) {
                placeAt(level, t, ground, Direction.UP);
                return;
            }
            t.cave = false;
            Engine.save();
        }
        Random h = new Random(d.seed + t.kind.hashCode());
        // Canopy: everything is in the trees. Altitude: on rock walls.
        if (Engine.is(d, "canopy") && (h.nextBoolean() ? onTreetop(level, t, bx, bz) || onWall(level, t, bx, bz, true)
                : onWall(level, t, bx, bz, true) || onTreetop(level, t, bx, bz))) return;
        if (Engine.is(d, "highlands") && onWall(level, t, bx, bz, false)) return;
        boolean scratches = t.kind.equals("stat");
        boolean wall = scratches || (t.kind.equals("type2") && h.nextBoolean()) || (t.kind.equals("type1") && h.nextInt(3) == 0);
        boolean treetop = t.kind.equals("egg") && h.nextBoolean();
        if (scratches && onWall(level, t, bx, bz, true)) return;
        if (wall && onWall(level, t, bx, bz, false)) return;
        if (treetop && onTreetop(level, t, bx, bz)) return;
        // First free spot on a solid block, spiralling outwards if needed (water, foliage...).
        for (int radius = 0; radius <= 20; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    int y = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState here = level.getBlockState(pos), below = level.getBlockState(pos.below());
                    if (!here.canBeReplaced() || !here.getFluidState().isEmpty() || !below.isFaceSturdy(level, pos.below(), Direction.UP)) continue;
                    placeAt(level, t, pos, Direction.UP);
                    return;
                }
            }
        }
        elsewhere(d, t);
    }

    // Nothing usable within twenty blocks (lake, swamp, dry land for an aquatic trace...): the trace moves elsewhere in the
    // zone and is placed on the next pass. Otherwise the kit would lead the player to an empty spot.
    private static void elsewhere(CaseFile d, Trace t) {
        double a = Engine.RANDOM.nextDouble() * Math.PI * 2, r = 6 + Engine.RANDOM.nextDouble() * (Engine.radius(d) - 6);
        t.x = Math.floor(d.zoneX + Math.cos(a) * r) + 0.5;
        t.z = Math.floor(d.zoneZ + Math.sin(a) * r) + 0.5;
        t.placed = false;
        Engine.save();
    }

    // Underground case: the floor of a cave below this point (two air blocks over a solid block, out of the sky, at least
    // eight blocks below the surface), spiralling outwards.
    static BlockPos cave(ServerLevel level, int bx, int bz, int radiusMax) {
        int minY = level.getMinBuildHeight() + 6;
        for (int radius = 0; radius <= radiusMax; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    var chunk = level.getChunk(x >> 4, z >> 4);
                    int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surface - 8, z);
                    boolean above = false; // is the block just above air?
                    for (int y = surface - 8; y > minY; y--) {
                        pos.setY(y);
                        BlockState here = chunk.getBlockState(pos);
                        boolean air = here.isAir();
                        if (!air && above && here.getFluidState().isEmpty() && here.isFaceSturdy(level, pos, Direction.UP)) {
                            BlockPos ground = new BlockPos(x, y + 1, z);
                            if (chunk.getBlockState(ground.above()).isAir() && !level.canSeeSky(ground)) return ground;
                        }
                        above = air;
                    }
                }
            }
        }
        return null;
    }

    // Cave trace: a cave floor 3 to 15 blocks below the surface that can be reached on foot from outside (never a sealed
    // pocket that would need digging). Spirals out up to 16 blocks from the intended spot.
    static BlockPos openCave(ServerLevel level, int bx, int bz) {
        int attempts = 0; // each path check visits up to 600 blocks, so their number is capped
        for (int radius = 0; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    var chunk = level.getChunk(x >> 4, z >> 4);
                    int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    int bottom = Math.max(surface - 15, level.getMinBuildHeight() + 1);
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surface - 1, z);
                    for (int y = surface - 3; y >= bottom; y--) {
                        pos.setY(y);
                        BlockState here = chunk.getBlockState(pos);
                        if (here.isAir() || !here.getFluidState().isEmpty() || !here.isFaceSturdy(level, pos, Direction.UP)) continue;
                        BlockPos ground = new BlockPos(x, y + 1, z);
                        if (!free(level, ground) || !free(level, ground.above()) || level.canSeeSky(ground)) continue;
                        if (linkedToSky(level, ground)) return ground;
                        if (++attempts >= 40) return null;
                    }
                }
            }
        }
        return null;
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState b = level.getBlockState(pos);
        return b.isAir() && b.getFluidState().isEmpty();
    }

    // Flood fill through air from the cave floor: true if an open-sky block is reached within 600 steps and 32 blocks.
    // Air blocks need air above or below so a player can pass (no one-block-high slits).
    private static boolean linkedToSky(ServerLevel level, BlockPos start) {
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && seen.size() < 600) {
            BlockPos p = queue.poll();
            if (level.canSeeSky(p)) return true;
            for (Direction dir : Direction.values()) {
                BlockPos v = p.relative(dir);
                if (seen.contains(v) || Math.abs(v.getX() - start.getX()) > 32 || Math.abs(v.getZ() - start.getZ()) > 32) continue;
                if (!level.hasChunk(v.getX() >> 4, v.getZ() >> 4) || !free(level, v)) continue;
                if (!free(level, v.above()) && !free(level, v.below())) continue;
                seen.add(v);
                queue.add(v);
            }
        }
        return false;
    }

    // Aquatic case: the trace lies on the bottom, preferably in shallow water (14 blocks at most).
    private static boolean underwater(ServerLevel level, Trace t, int bx, int bz) {
        for (int maxDepth : new int[] {14, 48}) {
            for (int radius = 0; radius <= 20; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        int x = bx + dx, z = bz + dz;
                        if (!level.hasChunk(x >> 4, z >> 4)) continue;
                        var chunk = level.getChunk(x >> 4, z >> 4);
                        int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) + 1;
                        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                        BlockPos pos = new BlockPos(x, y, z);
                        if (surface - y < 1 || surface - y > maxDepth || !level.getBlockState(pos).is(Blocks.WATER)) continue;
                        if (!level.getFluidState(pos).isSource() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
                        placeAt(level, t, pos, Direction.UP);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int material(BlockState ground) {
        return ground.is(BlockTags.DIRT) ? 0 : ground.is(BlockTags.SAND) ? 1 : ground.is(Blocks.GRAVEL) ? 2 : -1;
    }

    // Hides the clue underground: a loose soil block (dirt, sand, gravel) under one or two blocks is replaced with disturbed
    // soil. The player has to dig to find it, then brush it clear.
    private static boolean bury(ServerLevel level, Trace t, int bx, int bz) {
        for (int radius = 0; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    int surface = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    BlockState above = level.getBlockState(new BlockPos(x, surface + 1, z));
                    if (!above.canBeReplaced() || !above.getFluidState().isEmpty()) continue;
                    for (int depth = 2; depth >= 1; depth--) {
                        BlockPos pos = new BlockPos(x, surface - depth, z);
                        BlockState ground = level.getBlockState(pos);
                        boolean loose = material(ground) >= 0;
                        for (int y = pos.getY() + 1; y <= surface && loose; y++) loose = material(level.getBlockState(new BlockPos(x, y, z))) >= 0;
                        if (!loose || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
                        t.origin = BuiltInRegistries.BLOCK.getKey(ground.getBlock()).toString();
                        level.setBlock(pos, ModContent.DISTURBED_SOIL.defaultBlockState().setValue(ModContent.DisturbedSoilBlock.MATERIAL, material(ground)), 3);
                        t.px = x;
                        t.py = pos.getY();
                        t.pz = z;
                        t.x = x + 0.5;
                        t.z = z + 0.5;
                        t.placed = true;
                        t.brushing = 0;
                        Engine.save();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // The player brushes (brush held) disturbed soil from their case: four stages, then the clue appears in the hole.
    static void brush(ServerPlayer player, CaseFile d, ServerLevel level) {
        HitResult aimed = player.pick(5.0, 0f, false);
        if (!(aimed instanceof BlockHitResult hit) || aimed.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModContent.DISTURBED_SOIL)) return;
        Trace trace = null;
        for (Trace t : d.traces) {
            if (t.buried && !t.uncovered && !t.over() && t.placed && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ()) trace = t;
        }
        if (trace == null) return;
        trace.brushing++;
        int step = Math.min(3, trace.brushing / 2);
        level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 6, 0.25, 0.02, 0.25, 0.0);
        if (trace.brushing < 8) {
            if (state.getValue(ModContent.DisturbedSoilBlock.UNCOVERED) != step) {
                replacingSoil = true;
                level.setBlock(pos, state.setValue(ModContent.DisturbedSoilBlock.UNCOVERED, step), 3);
                replacingSoil = false;
            }
            return;
        }
        replacingSoil = true;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        replacingSoil = false;
        trace.uncovered = true;
        placeAt(level, trace, pos, Direction.UP);
        player.playNotifySound(SoundEvents.BRUSH_SAND_COMPLETED, SoundSource.PLAYERS, 1f, 1f);
        Engine.grantAdvancement(player, "clue_uncovered");
        Messages.announce(player, "clue_uncovered", 0x6CCB5F, Component.translatable(Genetics.traceName(trace.kind, true, trace.variant)));
        Messages.sync(player);
    }

    // Disturbed soil was broken (pickaxe, shovel, explosion, piston...): the clue it hid is lost.
    public static void groundDestroyed(Level level, BlockPos pos) {
        if (replacingSoil || !(level instanceof ServerLevel serverLevel)) return;
        String dim = level.dimension().location().toString();
        for (Map.Entry<UUID, CaseFile> entry : Engine.state.caseFiles.entrySet()) {
            CaseFile d = entry.getValue();
            if (!dim.equals(d.dimension)) continue;
            for (Trace t : d.traces) {
                if (!t.buried || t.uncovered || t.over() || !t.placed || t.px != pos.getX() || t.py != pos.getY() || t.pz != pos.getZ()) continue;
                t.lost = true;
                t.placed = false;
                d.faults++;
                Engine.save();
                List<ServerPlayer> present = Engine.online(d, serverLevel.getServer());
                ServerPlayer owner = present.isEmpty() ? null : present.get(0);
                if (owner != null) {
                    owner.playNotifySound(SoundEvents.TURTLE_EGG_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
                    Messages.announce(owner, "clue_destroyed", 0xE8543A, Component.translatable(Genetics.traceName(t.kind, true, t.variant)));
                    Messages.sync(owner);
                }
                return;
            }
        }
    }

    // Puts the original block back in place of a buried clue (once sampled, or when the case ends).
    static void restoreGround(ServerLevel level, Trace t) {
        BlockPos pos = new BlockPos(t.px, t.py, t.pz);
        BlockState current = level.getBlockState(pos);
        if (!current.is(ModContent.DISTURBED_SOIL) && !current.is(ModContent.TRACE) && !current.isAir()) return;
        var block = t.origin == null ? java.util.Optional.<net.minecraft.world.level.block.Block>empty()
                : BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(t.origin));
        replacingSoil = true;
        level.setBlock(pos, block.map(b -> b.defaultBlockState()).orElse(Blocks.DIRT.defaultBlockState()), 3);
        replacingSoil = false;
    }

    private static void placeAt(ServerLevel level, Trace t, BlockPos pos, Direction facing) {
        int kind = Genetics.MARKERS.indexOf(t.kind);
        if (t.buried) kind = Genetics.buriedBlock(t.kind, t.variant);
        level.setBlock(pos, ModContent.TRACE.defaultBlockState().setValue(ModContent.TraceBlock.KIND, kind)
                .setValue(ModContent.TraceBlock.FACING, facing).setValue(ModContent.TraceBlock.WATERLOGGED, level.getFluidState(pos).is(FluidTags.WATER)), 3);
        t.px = pos.getX();
        t.py = pos.getY();
        t.pz = pos.getZ();
        t.x = pos.getX() + 0.5;
        t.z = pos.getZ() + 0.5;
        t.placed = true;
        Engine.save();
    }

    // At eye level against a tree trunk (trunks = true) or against a solid wall at least two blocks high.
    private static boolean onWall(ServerLevel level, Trace t, int bx, int bz, boolean trunks) {
        int[] heights = {1, 0, 2};
        for (int radius = 0; radius <= 9; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    int ground = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    if (!level.getBlockState(new BlockPos(x, ground - 1, z)).isFaceSturdy(level, new BlockPos(x, ground - 1, z), Direction.UP)) continue;
                    for (int dy : heights) {
                        BlockPos pos = new BlockPos(x, ground + dy, z);
                        BlockState here = level.getBlockState(pos);
                        if (!here.canBeReplaced() || !here.getFluidState().isEmpty()) continue;
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            BlockPos behind = pos.relative(dir);
                            if (!level.hasChunk(behind.getX() >> 4, behind.getZ() >> 4)) continue;
                            BlockState support = level.getBlockState(behind);
                            boolean ok;
                            if (trunks) {
                                ok = support.is(BlockTags.LOGS);
                            } else {
                                ok = !support.is(BlockTags.LOGS) && !support.is(BlockTags.LEAVES) && support.isFaceSturdy(level, behind, dir.getOpposite())
                                        && level.getBlockState(behind.above()).isFaceSturdy(level, behind.above(), dir.getOpposite());
                            }
                            if (!ok) continue;
                            placeAt(level, t, pos, dir.getOpposite());
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean onTreetop(ServerLevel level, Trace t, int bx, int bz) {
        for (int radius = 0; radius <= 9; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level.hasChunk(x >> 4, z >> 4)) continue;
                    var chunk = level.getChunk(x >> 4, z >> 4);
                    int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
                    int ground = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    BlockPos pos = new BlockPos(x, top + 1, z);
                    if (top - ground < 3 || !level.getBlockState(pos.below()).is(BlockTags.LEAVES) || !level.getBlockState(pos).canBeReplaced()) continue;
                    placeAt(level, t, pos, Direction.UP);
                    return true;
                }
            }
        }
        return false;
    }

    static void removeTraces(ServerLevel level, CaseFile d) {
        for (Trace t : d.traces) {
            if (!t.placed || t.over()) continue;
            BlockPos pos = new BlockPos(t.px, t.py, t.pz);
            if (level.hasChunk(t.px >> 4, t.pz >> 4)) {
                if (t.buried) restoreGround(level, t);
                else if (level.getBlockState(pos).is(ModContent.TRACE)) level.removeBlock(pos, false);
            }
            t.placed = false;
        }
    }
}
