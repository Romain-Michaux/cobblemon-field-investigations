package fr.cobblescout.traque;

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
    private static boolean remplacement;

    // Où poser la trace : au sol par défaut, mais les griffures vont sur les troncs (ou une paroi), certains dépôts sur les
    // parois rocheuses, et un nid sur deux se trouve en haut d'un arbre. Le choix est fixe pour une trace donnée.
    static void poser(ServerLevel monde, Dossier d, Trace t) {
        int bx = (int) Math.floor(t.x), bz = (int) Math.floor(t.z);
        if (!monde.hasChunk(bx >> 4, bz >> 4)) return;
        if (t.posee) {
            BlockState la = monde.getBlockState(new BlockPos(t.px, t.py, t.pz));
            if (la.is(Contenu.TRACE) || (t.enfoui && !t.degage && la.is(Contenu.SOL))) return;
        }
        if (t.enfoui && !t.degage) {
            if (enfouir(monde, t, bx, bz)) return;
            t.enfoui = false; // aucun sol meuble par ici : l'indice affleure, comme une trace ordinaire
        }
        if (t.enfoui) {
            // Déjà dégagé mais disparu (eau, chute de sable...) : on le repose dans son trou.
            BlockPos trou = new BlockPos(t.px, t.py, t.pz);
            if (monde.getBlockState(trou).canBeReplaced()) fixer(monde, t, trou, Direction.UP);
            return;
        }
        if (Moteur.est(d, "aquatique")) {
            if (!sousLEau(monde, t, bx, bz)) ailleurs(d, t);
            return;
        }
        if (Moteur.est(d, "souterraine") && t.echecs < 6) {
            BlockPos sol = grotte(monde, bx, bz, 10);
            if (sol != null) {
                fixer(monde, t, sol, Direction.UP);
            } else {
                // Pas de grotte sous cet endroit : on cherche ailleurs dans la zone. Après six essais, la trace finit en surface.
                t.echecs++;
                ailleurs(d, t);
            }
            return;
        }
        if (t.grotte) {
            BlockPos sol = grotteOuverte(monde, bx, bz);
            if (sol != null) {
                fixer(monde, t, sol, Direction.UP);
                return;
            }
            t.grotte = false;
            Moteur.sauver();
        }
        Random h = new Random(d.graine + t.genre.hashCode());
        // Cimes : tout se trouve dans les arbres. Altitude : sur les parois rocheuses.
        if (Moteur.est(d, "cimes") && (h.nextBoolean() ? surCime(monde, t, bx, bz) || surParoi(monde, t, bx, bz, true)
                : surParoi(monde, t, bx, bz, true) || surCime(monde, t, bx, bz))) return;
        if (Moteur.est(d, "altitude") && surParoi(monde, t, bx, bz, false)) return;
        boolean griffures = t.genre.equals("stat");
        boolean paroi = griffures || (t.genre.equals("type2") && h.nextBoolean()) || (t.genre.equals("type1") && h.nextInt(3) == 0);
        boolean cime = t.genre.equals("oeuf") && h.nextBoolean();
        if (griffures && surParoi(monde, t, bx, bz, true)) return;
        if (paroi && surParoi(monde, t, bx, bz, false)) return;
        if (cime && surCime(monde, t, bx, bz)) return;
        // Premier emplacement libre posé sur un bloc plein, en s'écartant en spirale si besoin (eau, feuillage...).
        for (int rayon = 0; rayon <= 20; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    int y = monde.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState ici = monde.getBlockState(pos), dessous = monde.getBlockState(pos.below());
                    if (!ici.canBeReplaced() || !ici.getFluidState().isEmpty() || !dessous.isFaceSturdy(monde, pos.below(), Direction.UP)) continue;
                    fixer(monde, t, pos, Direction.UP);
                    return;
                }
            }
        }
        ailleurs(d, t);
    }

    // Rien de praticable à vingt blocs à la ronde (lac, marais, terre ferme pour une trace aquatique...) : la trace est
    // déplacée ailleurs dans la zone et sera posée au prochain passage. Sans cela, le kit guiderait vers un endroit vide.
    private static void ailleurs(Dossier d, Trace t) {
        double a = Moteur.HASARD.nextDouble() * Math.PI * 2, r = 6 + Moteur.HASARD.nextDouble() * (Moteur.rayon(d) - 6);
        t.x = Math.floor(d.zoneX + Math.cos(a) * r) + 0.5;
        t.z = Math.floor(d.zoneZ + Math.sin(a) * r) + 0.5;
        t.posee = false;
        Moteur.sauver();
    }

    // Enquête souterraine : le sol d'une grotte sous ce point (deux blocs d'air sur un bloc plein, à l'abri du ciel, au moins
    // huit blocs sous la surface), en s'écartant en spirale.
    static BlockPos grotte(ServerLevel monde, int bx, int bz, int rayonMax) {
        int fond = monde.getMinBuildHeight() + 6;
        for (int rayon = 0; rayon <= rayonMax; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    var chunk = monde.getChunk(x >> 4, z >> 4);
                    int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surface - 8, z);
                    boolean dessus = false; // le bloc juste au-dessus est-il de l'air ?
                    for (int y = surface - 8; y > fond; y--) {
                        pos.setY(y);
                        BlockState ici = chunk.getBlockState(pos);
                        boolean air = ici.isAir();
                        if (!air && dessus && ici.getFluidState().isEmpty() && ici.isFaceSturdy(monde, pos, Direction.UP)) {
                            BlockPos sol = new BlockPos(x, y + 1, z);
                            if (chunk.getBlockState(sol.above()).isAir() && !monde.canSeeSky(sol)) return sol;
                        }
                        dessus = air;
                    }
                }
            }
        }
        return null;
    }

    // Trace en grotte : un sol de grotte entre 3 et 15 blocs sous la surface, qu'on peut rejoindre à pied depuis l'extérieur
    // (jamais une poche fermée qu'il faudrait creuser). On s'écarte en spirale jusqu'à 16 blocs de l'endroit prévu.
    static BlockPos grotteOuverte(ServerLevel monde, int bx, int bz) {
        int essais = 0; // chaque vérification de passage parcourt jusqu'à 600 blocs : on en limite le nombre
        for (int rayon = 0; rayon <= 16; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    var chunk = monde.getChunk(x >> 4, z >> 4);
                    int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    int bas = Math.max(surface - 15, monde.getMinBuildHeight() + 1);
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, surface - 1, z);
                    for (int y = surface - 3; y >= bas; y--) {
                        pos.setY(y);
                        BlockState ici = chunk.getBlockState(pos);
                        if (ici.isAir() || !ici.getFluidState().isEmpty() || !ici.isFaceSturdy(monde, pos, Direction.UP)) continue;
                        BlockPos sol = new BlockPos(x, y + 1, z);
                        if (!libre(monde, sol) || !libre(monde, sol.above()) || monde.canSeeSky(sol)) continue;
                        if (relieeAuCiel(monde, sol)) return sol;
                        if (++essais >= 40) return null;
                    }
                }
            }
        }
        return null;
    }

    private static boolean libre(ServerLevel monde, BlockPos pos) {
        BlockState b = monde.getBlockState(pos);
        return b.isAir() && b.getFluidState().isEmpty();
    }

    // Parcours de l'air en partant du sol de la grotte : vrai si l'on atteint un bloc à ciel ouvert en moins de 600 pas,
    // sans s'éloigner de plus de 32 blocs. Les blocs d'air doivent avoir de l'air au-dessus ou en dessous pour qu'un
    // joueur puisse y passer (pas de fente d'un bloc de haut).
    private static boolean relieeAuCiel(ServerLevel monde, BlockPos depart) {
        java.util.ArrayDeque<BlockPos> file = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> vus = new java.util.HashSet<>();
        file.add(depart);
        vus.add(depart);
        while (!file.isEmpty() && vus.size() < 600) {
            BlockPos p = file.poll();
            if (monde.canSeeSky(p)) return true;
            for (Direction dir : Direction.values()) {
                BlockPos v = p.relative(dir);
                if (vus.contains(v) || Math.abs(v.getX() - depart.getX()) > 32 || Math.abs(v.getZ() - depart.getZ()) > 32) continue;
                if (!monde.hasChunk(v.getX() >> 4, v.getZ() >> 4) || !libre(monde, v)) continue;
                if (!libre(monde, v.above()) && !libre(monde, v.below())) continue;
                vus.add(v);
                file.add(v);
            }
        }
        return false;
    }

    // Enquête aquatique : la trace repose au fond de l'eau, de préférence par petit fond (14 blocs au plus).
    private static boolean sousLEau(ServerLevel monde, Trace t, int bx, int bz) {
        for (int fondMax : new int[] {14, 48}) {
            for (int rayon = 0; rayon <= 20; rayon++) {
                for (int dx = -rayon; dx <= rayon; dx++) {
                    for (int dz = -rayon; dz <= rayon; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                        int x = bx + dx, z = bz + dz;
                        if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                        var chunk = monde.getChunk(x >> 4, z >> 4);
                        int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15) + 1;
                        int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                        BlockPos pos = new BlockPos(x, y, z);
                        if (surface - y < 1 || surface - y > fondMax || !monde.getBlockState(pos).is(Blocks.WATER)) continue;
                        if (!monde.getFluidState(pos).isSource() || !monde.getBlockState(pos.below()).isFaceSturdy(monde, pos.below(), Direction.UP)) continue;
                        fixer(monde, t, pos, Direction.UP);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int matiere(BlockState sol) {
        return sol.is(BlockTags.DIRT) ? 0 : sol.is(BlockTags.SAND) ? 1 : sol.is(Blocks.GRAVEL) ? 2 : -1;
    }

    // Cache l'indice sous la surface : un bloc de sol meuble (terre, sable, gravier), recouvert d'un ou deux blocs,
    // est remplacé par un sol remué. Il faut creuser pour le trouver, puis le dégager au pinceau.
    private static boolean enfouir(ServerLevel monde, Trace t, int bx, int bz) {
        for (int rayon = 0; rayon <= 16; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    int surface = monde.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    BlockState dessus = monde.getBlockState(new BlockPos(x, surface + 1, z));
                    if (!dessus.canBeReplaced() || !dessus.getFluidState().isEmpty()) continue;
                    for (int profondeur = 2; profondeur >= 1; profondeur--) {
                        BlockPos pos = new BlockPos(x, surface - profondeur, z);
                        BlockState sol = monde.getBlockState(pos);
                        boolean meuble = matiere(sol) >= 0;
                        for (int y = pos.getY() + 1; y <= surface && meuble; y++) meuble = matiere(monde.getBlockState(new BlockPos(x, y, z))) >= 0;
                        if (!meuble || !monde.getBlockState(pos.below()).isFaceSturdy(monde, pos.below(), Direction.UP)) continue;
                        t.origine = BuiltInRegistries.BLOCK.getKey(sol.getBlock()).toString();
                        monde.setBlock(pos, Contenu.SOL.defaultBlockState().setValue(Contenu.BlocSolRemue.MATIERE, matiere(sol)), 3);
                        t.px = x;
                        t.py = pos.getY();
                        t.pz = z;
                        t.x = x + 0.5;
                        t.z = z + 0.5;
                        t.posee = true;
                        t.brossage = 0;
                        Moteur.sauver();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Le joueur brosse (pinceau maintenu) un sol remué de son enquête : quatre étapes, puis l'indice apparaît dans le trou.
    static void brosser(ServerPlayer joueur, Dossier d, ServerLevel monde) {
        HitResult vise = joueur.pick(5.0, 0f, false);
        if (!(vise instanceof BlockHitResult coup) || vise.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = coup.getBlockPos();
        BlockState etat = monde.getBlockState(pos);
        if (!etat.is(Contenu.SOL)) return;
        Trace trace = null;
        for (Trace t : d.traces) {
            if (t.enfoui && !t.degage && !t.finie() && t.posee && t.px == pos.getX() && t.py == pos.getY() && t.pz == pos.getZ()) trace = t;
        }
        if (trace == null) return;
        trace.brossage++;
        int etape = Math.min(3, trace.brossage / 2);
        monde.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 6, 0.25, 0.02, 0.25, 0.0);
        if (trace.brossage < 8) {
            if (etat.getValue(Contenu.BlocSolRemue.DEGAGE) != etape) {
                remplacement = true;
                monde.setBlock(pos, etat.setValue(Contenu.BlocSolRemue.DEGAGE, etape), 3);
                remplacement = false;
            }
            return;
        }
        remplacement = true;
        monde.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        remplacement = false;
        trace.degage = true;
        fixer(monde, trace, pos, Direction.UP);
        joueur.playNotifySound(SoundEvents.BRUSH_SAND_COMPLETED, SoundSource.PLAYERS, 1f, 1f);
        Moteur.progres(joueur, "indice_degage");
        Messages.annonce(joueur, "indice_degage", 0x6CCB5F, Component.translatable(Genetique.nomTrace(trace.genre, true, trace.variante)));
        Messages.synchroniser(joueur);
    }

    // Un sol remué a été cassé (pioche, pelle, explosion, piston...) : l'indice qu'il cachait est perdu.
    public static void solDetruit(Level monde, BlockPos pos) {
        if (remplacement || !(monde instanceof ServerLevel serveur)) return;
        String dim = monde.dimension().location().toString();
        for (Map.Entry<UUID, Dossier> en : Moteur.etat.dossiers.entrySet()) {
            Dossier d = en.getValue();
            if (!dim.equals(d.monde)) continue;
            for (Trace t : d.traces) {
                if (!t.enfoui || t.degage || t.finie() || !t.posee || t.px != pos.getX() || t.py != pos.getY() || t.pz != pos.getZ()) continue;
                t.perdue = true;
                t.posee = false;
                d.fautes++;
                Moteur.sauver();
                List<ServerPlayer> presents = Moteur.enLigne(d, serveur.getServer());
                ServerPlayer proprietaire = presents.isEmpty() ? null : presents.get(0);
                if (proprietaire != null) {
                    proprietaire.playNotifySound(SoundEvents.TURTLE_EGG_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
                    Messages.annonce(proprietaire, "indice_detruit", 0xE8543A, Component.translatable(Genetique.nomTrace(t.genre, true, t.variante)));
                    Messages.synchroniser(proprietaire);
                }
                return;
            }
        }
    }

    // Remet le bloc d'origine à la place d'un indice enfoui (une fois prélevé, ou quand l'enquête se termine).
    static void reboucher(ServerLevel monde, Trace t) {
        BlockPos pos = new BlockPos(t.px, t.py, t.pz);
        BlockState la = monde.getBlockState(pos);
        if (!la.is(Contenu.SOL) && !la.is(Contenu.TRACE) && !la.isAir()) return;
        var bloc = t.origine == null ? java.util.Optional.<net.minecraft.world.level.block.Block>empty()
                : BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(t.origine));
        remplacement = true;
        monde.setBlock(pos, bloc.map(b -> b.defaultBlockState()).orElse(Blocks.DIRT.defaultBlockState()), 3);
        remplacement = false;
    }

    private static void fixer(ServerLevel monde, Trace t, BlockPos pos, Direction appui) {
        int genre = Genetique.MARQUEURS.indexOf(t.genre);
        if (t.enfoui) genre = Genetique.blocEnfoui(t.genre, t.variante);
        monde.setBlock(pos, Contenu.TRACE.defaultBlockState().setValue(Contenu.BlocTrace.GENRE, genre)
                .setValue(Contenu.BlocTrace.APPUI, appui).setValue(Contenu.BlocTrace.EAU, monde.getFluidState(pos).is(FluidTags.WATER)), 3);
        t.px = pos.getX();
        t.py = pos.getY();
        t.pz = pos.getZ();
        t.x = pos.getX() + 0.5;
        t.z = pos.getZ() + 0.5;
        t.posee = true;
        Moteur.sauver();
    }

    // Plaquée à hauteur de regard contre un tronc (troncs = true) ou contre une paroi pleine d'au moins deux blocs de haut.
    private static boolean surParoi(ServerLevel monde, Trace t, int bx, int bz, boolean troncs) {
        int[] hauteurs = {1, 0, 2};
        for (int rayon = 0; rayon <= 9; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    int sol = monde.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                    if (!monde.getBlockState(new BlockPos(x, sol - 1, z)).isFaceSturdy(monde, new BlockPos(x, sol - 1, z), Direction.UP)) continue;
                    for (int dy : hauteurs) {
                        BlockPos pos = new BlockPos(x, sol + dy, z);
                        BlockState ici = monde.getBlockState(pos);
                        if (!ici.canBeReplaced() || !ici.getFluidState().isEmpty()) continue;
                        for (Direction dir : Direction.Plane.HORIZONTAL) {
                            BlockPos derriere = pos.relative(dir);
                            if (!monde.hasChunk(derriere.getX() >> 4, derriere.getZ() >> 4)) continue;
                            BlockState appui = monde.getBlockState(derriere);
                            boolean ok;
                            if (troncs) {
                                ok = appui.is(BlockTags.LOGS);
                            } else {
                                ok = !appui.is(BlockTags.LOGS) && !appui.is(BlockTags.LEAVES) && appui.isFaceSturdy(monde, derriere, dir.getOpposite())
                                        && monde.getBlockState(derriere.above()).isFaceSturdy(monde, derriere.above(), dir.getOpposite());
                            }
                            if (!ok) continue;
                            fixer(monde, t, pos, dir.getOpposite());
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean surCime(ServerLevel monde, Trace t, int bx, int bz) {
        for (int rayon = 0; rayon <= 9; rayon++) {
            for (int dx = -rayon; dx <= rayon; dx++) {
                for (int dz = -rayon; dz <= rayon; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != rayon) continue;
                    int x = bx + dx, z = bz + dz;
                    if (!monde.hasChunk(x >> 4, z >> 4)) continue;
                    var chunk = monde.getChunk(x >> 4, z >> 4);
                    int haut = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
                    int sol = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
                    BlockPos pos = new BlockPos(x, haut + 1, z);
                    if (haut - sol < 3 || !monde.getBlockState(pos.below()).is(BlockTags.LEAVES) || !monde.getBlockState(pos).canBeReplaced()) continue;
                    fixer(monde, t, pos, Direction.UP);
                    return true;
                }
            }
        }
        return false;
    }

    static void retirerTraces(ServerLevel monde, Dossier d) {
        for (Trace t : d.traces) {
            if (!t.posee || t.finie()) continue;
            BlockPos pos = new BlockPos(t.px, t.py, t.pz);
            if (monde.hasChunk(t.px >> 4, t.pz >> 4)) {
                if (t.enfoui) reboucher(monde, t);
                else if (monde.getBlockState(pos).is(Contenu.TRACE)) monde.removeBlock(pos, false);
            }
            t.posee = false;
        }
    }
}
