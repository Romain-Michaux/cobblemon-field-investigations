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

// Pose des traces dans le monde : en surface, sur paroi, dans les cimes, sous l'eau, sous terre ou enfouies.
final class Traces {
    private Traces() {}

    // Vrai pendant que le mod remplace lui-même un sol remué (dégagement, nettoyage) : ce n'est pas une destruction.
    private static boolean replacement;

    // Où poser la trace : au sol par défaut, mais les griffures vont sur les troncs (ou une paroi), certains dépôts sur les
    // parois rocheuses, et un nid sur deux se trouve en haut d'un arbre. Le choix est fixe pour une trace donnée.
    static void placeIt(ServerLevel level1, CaseFile d, Trace t) {
        int bx = (int) Math.floor(t.x), bz = (int) Math.floor(t.z);
        if (!level1.hasChunk(bx >> 4, bz >> 4)) return;
        if (t.placedOne) {
            BlockState la = level1.getBlockState(new BlockPos(t.px, t.py, t.pz));
            if (la.is(PacketContent.TRACE) || (t.buried && !t.uncovered && la.is(PacketContent.SOIL))) return;
        }
        if (t.buried && !t.uncovered) {
            if (bury(level1, t, bx, bz)) return;
            t.buried = false; // aucun sol meuble par ici : l'indice affleure, comme une trace ordinaire
        }
        if (t.buried) {
            // Déjà dégagé mais disparu (eau, chute de sable...) : on le repose dans son trou.
            BlockPos hole = new BlockPos(t.px, t.py, t.pz);
            if (level1.getBlockState(hole).canBeReplaced()) fix(level1, t, hole, Direction.UP);
            return;
        }
        if (Engine.is(d, "aquatic")) {
            if (!underwater(level1, t, bx, bz)) elsewhere(d, t);
            return;
        }
        if (Engine.is(d, "underground") && t.failures < 6) {
            BlockPos ground = cave(level1, bx, bz, 10);
            if (ground != null) {
                fix(level1, t, ground, Direction.UP);
            } else {
                // Pas de grotte sous cet endroit : on cherche ailleurs dans la zone. Après six essais, la trace finit en surface.
                t.failures++;
                elsewhere(d, t);
            }
            return;
        }
        if (t.cave) {
            BlockPos ground = openCave(level1, bx, bz);
            if (ground != null) {
                fix(level1, t, ground, Direction.UP);
                return;
            }
            t.cave = false;
            Engine.save();
        }
        Random h = new Random(d.seed + t.kind.hashCode());
        // Cimes : tout se trouve dans les arbres. Altitude : sur les parois rocheuses.
        if (Engine.is(d, "canopy") && (h.nextBoolean() ? onTreetop(level1, t, bx, bz) || onWall(level1, t, bx, bz, true)
                : onWall(level1, t, bx, bz, true) || onTreetop(level1, t, bx, bz))) return;
        if (Engine.is(d, "altitude") && onWall(level1, t, bx, bz, false)) return;
        boolean scratches = t.kind.equals("stat");
        boolean wall = scratches || (t.kind.equals("type2") && h.nextBoolean()) || (t.kind.equals("type1") && h.nextInt(3) == 0);
        boolean treetop = t.kind.equals("egg") && h.nextBoolean();
        if (scratches && onWall(level1, t, bx, bz, true)) return;
        if (wall && onWall(level1, t, bx, bz, false)) return;
        if (treetop && onTreetop(level1, t, bx, bz)) return;
        // Premier emplacement libre posé sur un bloc plein, en s'écartant en spirale si besoin (eau, feuillage...).
        for (int radius = 0; radius <= 20; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level1.hasChunk(x >> 4, z >> 4)) continue;
                    int y = level1.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState here = level1.getBlockState(pos), below = level1.getBlockState(pos.below());
                    if (!here.canBeReplaced() || !here.getFluidState().isEmpty() || !below.isFaceSturdy(level1, pos.below(), Direction.UP)) continue;
                    fix(level1, t, pos, Direction.UP);
                    return;
                }
            }
        }
        elsewhere(d, t);
    }

    // Rien de praticable à vingt blocs à la ronde (lac, marais, terre ferme pour une trace aquatique...) : la trace est
    // déplacée ailleurs dans la zone et sera posée au prochain passage. Sans cela, le kit guiderait vers un endroit vide.
    private static void elsewhere(CaseFile d, Trace t) {
        double a = Engine.RANDOM.nextDouble() * Math.PI * 2, r = 6 + Engine.RANDOM.nextDouble() * (Engine.radius(d) - 6);
        t.x = Math.floor(d.zoneX + Math.cos(a) * r) + 0.5;
        t.z = Math.floor(d.zoneZ + Math.sin(a) * r) + 0.5;
        t.placedOne = false;
        Engine.save();
    }

    // Enquête souterraine : le sol d'une grotte sous ce point (deux blocs d'air sur un bloc plein, à l'abri du ciel, au moins
    // huit blocs sous la surface), en s'écartant en spirale.
    static BlockPos cave(ServerLevel level1, int bx, int bz, int radiusMax) {
        int background = level1.getMinBuildHeight() + 6;
        for (int radius = 0; radius <= radiusMax; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level1.hasChunk(x >> 4, z >> 4)) continue;
                    var chunk = level1.getChunk(x >> 4, z >> 4);
                    int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surface - 8, z);
                    boolean above = false; // le bloc juste au-dessus est-il de l'air ?
                    for (int y = surface - 8; y > background; y--) {
                        pos.setY(y);
                        BlockState here = chunk.getBlockState(pos);
                        boolean air = here.isAir();
                        if (!air && above && here.getFluidState().isEmpty() && here.isFaceSturdy(level1, pos, Direction.UP)) {
                            BlockPos ground = new BlockPos(x, y + 1, z);
                            if (chunk.getBlockState(ground.above()).isAir() && !level1.canSeeSky(ground)) return ground;
                        }
                        above = air;
                    }
                }
            }
        }
        return null;
    }

    // Trace en grotte : un sol de grotte entre 3 et 15 blocs sous la surface, qu'on peut rejoindre à pied depuis l'extérieur
    // (jamais une poche fermée qu'il faudrait creuser). On s'écarte en spirale jusqu'à 16 blocs de l'endroit prévu.
    static BlockPos openCave(ServerLevel level1, int bx, int bz) {
        int attempts = 0; // chaque vérification de passage parcourt jusqu'à 600 blocs : on en limite le nombre
        for (int radius = 0; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level1.hasChunk(x >> 4, z >> 4)) continue;
                    var chunk = level1.getChunk(x >> 4, z >> 4);
                    int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    int bottom = Math.max(surface - 15, level1.getMinBuildHeight() + 1);
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surface - 1, z);
                    for (int y = surface - 3; y >= bottom; y--) {
                        pos.setY(y);
                        BlockState here = chunk.getBlockState(pos);
                        if (here.isAir() || !here.getFluidState().isEmpty() || !here.isFaceSturdy(level1, pos, Direction.UP)) continue;
                        BlockPos ground = new BlockPos(x, y + 1, z);
                        if (!free(level1, ground) || !free(level1, ground.above()) || level1.canSeeSky(ground)) continue;
                        if (linkedToSky(level1, ground)) return ground;
                        if (++attempts >= 40) return null;
                    }
                }
            }
        }
        return null;
    }

    private static boolean free(ServerLevel level1, BlockPos pos) {
        BlockState b = level1.getBlockState(pos);
        return b.isAir() && b.getFluidState().isEmpty();
    }

    // Parcours de l'air en partant du sol de la grotte : vrai si l'on atteint un bloc à ciel ouvert en moins de 600 pas,
    // sans s'éloigner de plus de 32 blocs. Les blocs d'air doivent avoir de l'air au-dessus ou en dessous pour qu'un
    // joueur puisse y passer (pas de fente d'un bloc de haut).
    private static boolean linkedToSky(ServerLevel level1, BlockPos departure) {
        java.util.ArrayDeque<BlockPos> file = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        file.add(departure);
        seen.add(departure);
        while (!file.isEmpty() && seen.size() < 600) {
            BlockPos p = file.poll();
            if (level1.canSeeSky(p)) return true;
            for (Direction dir : Direction.values()) {
                BlockPos v = p.relative(dir);
                if (seen.contains(v) || Math.abs(v.getX() - departure.getX()) > 32 || Math.abs(v.getZ() - departure.getZ()) > 32) continue;
                if (!level1.hasChunk(v.getX() >> 4, v.getZ() >> 4) || !free(level1, v)) continue;
                if (!free(level1, v.above()) && !free(level1, v.below())) continue;
                seen.add(v);
                file.add(v);
            }
        }
        return false;
    }

    // Enquête aquatique : la trace repose au fond de l'eau, de préférence par petit fond (14 blocs au plus).
    private static boolean underwater(ServerLevel level1, Trace t, int bx, int bz) {
        for (int backgroundMax : new int[] {14, 48}) {
            for (int radius = 0; radius <= 20; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        int x = bx + dx, z = bz + dz;
                        if (!level1.hasChunk(x >> 4, z >> 4)) continue;
                        var chunk = level1.getChunk(x >> 4, z >> 4);
                        int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) + 1;
                        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                        BlockPos pos = new BlockPos(x, y, z);
                        if (surface - y < 1 || surface - y > backgroundMax || !level1.getBlockState(pos).is(Blocks.WATER)) continue;
                        if (!level1.getFluidState(pos).isSource() || !level1.getBlockState(pos.below()).isFaceSturdy(level1, pos.below(), Direction.UP)) continue;
                        fix(level1, t, pos, Direction.UP);
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

    // Cache l'indice sous la surface : un bloc de sol meuble (terre, sable, gravier), recouvert d'un ou deux blocs,
    // est remplacé par un sol remué. Il faut creuser pour le trouver, puis le dégager au pinceau.
    private static boolean bury(ServerLevel level1, Trace t, int bx, int bz) {
        for (int radius = 0; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level1.hasChunk(x >> 4, z >> 4)) continue;
                    int surface = level1.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    BlockState above = level1.getBlockState(new BlockPos(x, surface + 1, z));
                    if (!above.canBeReplaced() || !above.getFluidState().isEmpty()) continue;
                    for (int depth = 2; depth >= 1; depth--) {
                        BlockPos pos = new BlockPos(x, surface - depth, z);
                        BlockState ground = level1.getBlockState(pos);
                        boolean loose = material(ground) >= 0;
                        for (int y = pos.getY() + 1; y <= surface && loose; y++) loose = material(level1.getBlockState(new BlockPos(x, y, z))) >= 0;
                        if (!loose || !level1.getBlockState(pos.below()).isFaceSturdy(level1, pos.below(), Direction.UP)) continue;
                        t.origin = BuiltInRegistries.BLOCK.getKey(ground.getBlock()).toString();
                        level1.setBlock(pos, PacketContent.SOIL.defaultBlockState().setValue(PacketContent.DisturbedSoilBlock.MATERIAL, material(ground)), 3);
                        t.px = x;
                        t.py = pos.getY();
                        t.pz = z;
                        t.x = x + 0.5;
                        t.z = z + 0.5;
                        t.placedOne = true;
                        t.brushing = 0;
                        Engine.save();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Le joueur brosse (pinceau maintenu) un sol remué de son enquête : quatre étapes, puis l'indice apparaît dans le trou.
    static void brush(ServerPlayer player, CaseFile d, ServerLevel level1) {
        HitResult aimed = player.pick(5.0, 0f, false);
        if (!(aimed instanceof BlockHitResult stroke) || aimed.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = stroke.getBlockPos();
        BlockState state = level1.getBlockState(pos);
        if (!state.is(PacketContent.SOIL)) return;
        Trace trace = null;
        for (Trace t : d.traces) {
            if (t.buried && !t.uncovered && !t.over() && t.placedOne && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ()) trace = t;
        }
        if (trace == null) return;
        trace.brushing++;
        int step = Math.min(3, trace.brushing / 2);
        level1.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 6, 0.25, 0.02, 0.25, 0.0);
        if (trace.brushing < 8) {
            if (state.getValue(PacketContent.DisturbedSoilBlock.UNCOVERED) != step) {
                replacement = true;
                level1.setBlock(pos, state.setValue(PacketContent.DisturbedSoilBlock.UNCOVERED, step), 3);
                replacement = false;
            }
            return;
        }
        replacement = true;
        level1.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        replacement = false;
        trace.uncovered = true;
        fix(level1, trace, pos, Direction.UP);
        player.playNotifySound(SoundEvents.BRUSH_SAND_COMPLETED, SoundSource.PLAYERS, 1f, 1f);
        Engine.progress1(player, "clue_uncovered");
        Messages.announce(player, "clue_uncovered", 0x6CCB5F, Component.translatable(Genetics.traceName(trace.kind, true, trace.variant)));
        Messages.sync(player);
    }

    // Un sol remué a été cassé (pioche, pelle, explosion, piston...) : l'indice qu'il cachait est perdu.
    public static void groundDestroyed(Level level1, BlockPos pos) {
        if (replacement || !(level1 instanceof ServerLevel server1)) return;
        String dim = level1.dimension().location().toString();
        for (Map.Entry<UUID, CaseFile> en : Engine.state.caseFiles.entrySet()) {
            CaseFile d = en.getValue();
            if (!dim.equals(d.level1)) continue;
            for (Trace t : d.traces) {
                if (!t.buried || t.uncovered || t.over() || !t.placedOne || t.px != pos.getX() || t.py != pos.getY() || t.pz != pos.getZ()) continue;
                t.lost = true;
                t.placedOne = false;
                d.faults++;
                Engine.save();
                List<ServerPlayer> present = Engine.online(d, server1.getServer());
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

    // Remet le bloc d'origine à la place d'un indice enfoui (une fois prélevé, ou quand l'enquête se termine).
    static void reboucher(ServerLevel level1, Trace t) {
        BlockPos pos = new BlockPos(t.px, t.py, t.pz);
        BlockState la = level1.getBlockState(pos);
        if (!la.is(PacketContent.SOIL) && !la.is(PacketContent.TRACE) && !la.isAir()) return;
        var block = t.origin == null ? java.util.Optional.<net.minecraft.world.level.block.Block>empty()
                : BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(t.origin));
        replacement = true;
        level1.setBlock(pos, block.map(b -> b.defaultBlockState()).orElse(Blocks.DIRT.defaultBlockState()), 3);
        replacement = false;
    }

    private static void fix(ServerLevel level1, Trace t, BlockPos pos, Direction press) {
        int kind = Genetics.MARKERS.indexOf(t.kind);
        if (t.buried) kind = Genetics.buriedBlock(t.kind, t.variant);
        level1.setBlock(pos, PacketContent.TRACE.defaultBlockState().setValue(PacketContent.TraceBlock.KIND, kind)
                .setValue(PacketContent.TraceBlock.PRESS, press).setValue(PacketContent.TraceBlock.WATER_BG, level1.getFluidState(pos).is(FluidTags.WATER)), 3);
        t.px = pos.getX();
        t.py = pos.getY();
        t.pz = pos.getZ();
        t.x = pos.getX() + 0.5;
        t.z = pos.getZ() + 0.5;
        t.placedOne = true;
        Engine.save();
    }

    // Plaquée à hauteur de regard contre un tronc (troncs = true) ou contre une paroi pleine d'au moins deux blocs de haut.
    private static boolean onWall(ServerLevel level1, Trace t, int bx, int bz, boolean trunks) {
        int[] heights = {1, 0, 2};
        for (int radius = 0; radius <= 9; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level1.hasChunk(x >> 4, z >> 4)) continue;
                    int ground = level1.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    if (!level1.getBlockState(new BlockPos(x, ground - 1, z)).isFaceSturdy(level1, new BlockPos(x, ground - 1, z), Direction.UP)) continue;
                    for (int dy : heights) {
                        BlockPos pos = new BlockPos(x, ground + dy, z);
                        BlockState here = level1.getBlockState(pos);
                        if (!here.canBeReplaced() || !here.getFluidState().isEmpty()) continue;
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            BlockPos behind = pos.relative(dir);
                            if (!level1.hasChunk(behind.getX() >> 4, behind.getZ() >> 4)) continue;
                            BlockState press = level1.getBlockState(behind);
                            boolean ok;
                            if (trunks) {
                                ok = press.is(BlockTags.LOGS);
                            } else {
                                ok = !press.is(BlockTags.LOGS) && !press.is(BlockTags.LEAVES) && press.isFaceSturdy(level1, behind, dir.getOpposite())
                                        && level1.getBlockState(behind.above()).isFaceSturdy(level1, behind.above(), dir.getOpposite());
                            }
                            if (!ok) continue;
                            fix(level1, t, pos, dir.getOpposite());
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean onTreetop(ServerLevel level1, Trace t, int bx, int bz) {
        for (int radius = 0; radius <= 9; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!level1.hasChunk(x >> 4, z >> 4)) continue;
                    var chunk = level1.getChunk(x >> 4, z >> 4);
                    int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
                    int ground = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    BlockPos pos = new BlockPos(x, top + 1, z);
                    if (top - ground < 3 || !level1.getBlockState(pos.below()).is(BlockTags.LEAVES) || !level1.getBlockState(pos).canBeReplaced()) continue;
                    fix(level1, t, pos, Direction.UP);
                    return true;
                }
            }
        }
        return false;
    }

    static void removeTraces(ServerLevel level1, CaseFile d) {
        for (Trace t : d.traces) {
            if (!t.placedOne || t.over()) continue;
            BlockPos pos = new BlockPos(t.px, t.py, t.pz);
            if (level1.hasChunk(t.px >> 4, t.pz >> 4)) {
                if (t.buried) reboucher(level1, t);
                else if (level1.getBlockState(pos).is(PacketContent.TRACE)) level1.removeBlock(pos, false);
            }
            t.placedOne = false;
        }
    }
}
