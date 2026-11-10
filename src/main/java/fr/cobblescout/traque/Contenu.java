package fr.cobblescout.traque;

import java.util.List;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// Blocs et objets du mod : traces et sol remué dans le monde, kit de terrain, fioles et échantillons.
public final class Contenu {
    public static final String NS = "cobblemon_field_investigations";
    public static Block TRACE, SOL;
    public static Item KIT, FIOLE, ECHANTILLON;
    // Renseigné par le client à chaque mise à jour de l'enquête : vrai quand la cible attend d'être scannée.
    public static volatile boolean scanCoteClient;

    private Contenu() {}

    static ResourceLocation id(String chemin) {
        return ResourceLocation.fromNamespaceAndPath(NS, chemin);
    }

    static void enregistrer() {
        TRACE = Registry.register(BuiltInRegistries.BLOCK, id("trace"), new BlocTrace(BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE).noCollission().instabreak().noLootTable().noOcclusion().sound(SoundType.MOSS_CARPET)
                .pushReaction(PushReaction.DESTROY)));
        SOL = Registry.register(BuiltInRegistries.BLOCK, id("sol_remue"), new BlocSolRemue(BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT).strength(0.8f).noLootTable().sound(SoundType.GRAVEL)));
        KIT = Registry.register(BuiltInRegistries.ITEM, id("kit_terrain"), new KitTerrain(new Item.Properties().stacksTo(1)));
        FIOLE = Registry.register(BuiltInRegistries.ITEM, id("fiole"), new Item(new Item.Properties().stacksTo(16)));
        ECHANTILLON = Registry.register(BuiltInRegistries.ITEM, id("echantillon"), new Echantillon(new Item.Properties().stacksTo(1)));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> {
            e.accept(KIT);
            e.accept(FIOLE);
        });
    }

    static CompoundTag donnees(ItemStack pile) {
        return pile.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    // Trace laissée par le Pokémon : un décalque posé au sol, qu'on prélève avec le kit de terrain.
    public static class BlocTrace extends Block implements net.minecraft.world.level.block.SimpleWaterloggedBlock {
        // Vrai quand la trace est au fond de l'eau (enquêtes aquatiques) : l'eau reste en place autour d'elle.
        public static final net.minecraft.world.level.block.state.properties.BooleanProperty EAU = BlockStateProperties.WATERLOGGED;
        // 0 à 4 : les cinq traces de surface ; 5 à 7 : les indices dégagés du sol (os, coquilles, croc).
        public static final IntegerProperty GENRE = IntegerProperty.create("genre", 0, 7);
        // UP = posée au sol (ou sur un feuillage) ; sinon plaquée contre le bloc situé derrière elle (tronc, falaise).
        public static final DirectionProperty APPUI = BlockStateProperties.FACING;
        private static final VoxelShape FORME = Block.box(1, 0, 1, 15, 2, 15);
        private static final VoxelShape NORD = Block.box(1, 1, 14, 15, 15, 16), SUD = Block.box(1, 1, 0, 15, 15, 2);
        private static final VoxelShape EST = Block.box(0, 1, 1, 2, 15, 15), OUEST = Block.box(14, 1, 1, 16, 15, 15);

        public BlocTrace(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(GENRE, 0).setValue(APPUI, Direction.UP).setValue(EAU, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(GENRE, APPUI, EAU);
        }

        @Override
        protected net.minecraft.world.level.material.FluidState getFluidState(BlockState etat) {
            return etat.getValue(EAU) ? net.minecraft.world.level.material.Fluids.WATER.getSource(false) : super.getFluidState(etat);
        }

        @Override
        protected BlockState updateShape(BlockState etat, Direction dir, BlockState voisin, net.minecraft.world.level.LevelAccessor monde, BlockPos pos, BlockPos posVoisin) {
            if (etat.getValue(EAU)) monde.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER, net.minecraft.world.level.material.Fluids.WATER.getTickDelay(monde));
            return super.updateShape(etat, dir, voisin, monde, pos, posVoisin);
        }

        @Override
        protected VoxelShape getShape(BlockState etat, BlockGetter monde, BlockPos pos, CollisionContext ctx) {
            return switch (etat.getValue(APPUI)) {
                case NORTH -> NORD;
                case SOUTH -> SUD;
                case EAST -> EST;
                case WEST -> OUEST;
                default -> FORME;
            };
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState etat, Level monde, BlockPos pos, Player joueur, BlockHitResult coup) {
            if (!monde.isClientSide && joueur instanceof ServerPlayer j) {
                Moteur.annonce(j, "kit_requis", 0xF2C93A);
            }
            return InteractionResult.sidedSuccess(monde.isClientSide);
        }
    }

    // Sol remué : un indice y est enfoui. On le dégage au pinceau, comme un sable suspect ; le casser détruit l'indice.
    public static class BlocSolRemue extends Block {
        public static final IntegerProperty MATIERE = IntegerProperty.create("matiere", 0, 2); // terre, sable, gravier
        public static final IntegerProperty DEGAGE = IntegerProperty.create("degage", 0, 3);

        public BlocSolRemue(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(MATIERE, 0).setValue(DEGAGE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(MATIERE, DEGAGE);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState etat, Level monde, BlockPos pos, Player joueur, BlockHitResult coup) {
            if (!monde.isClientSide && joueur instanceof ServerPlayer j) {
                Moteur.annonce(j, "sol_remue", 0xF2C93A);
            }
            return InteractionResult.sidedSuccess(monde.isClientSide);
        }

        // Pinceau en main : on laisse l'objet agir (sinon le message ci-dessus intercepterait le clic et le brossage ne démarrerait jamais).
        @Override
        protected ItemInteractionResult useItemOn(ItemStack pile, BlockState etat, Level monde, BlockPos pos, Player joueur, InteractionHand main, BlockHitResult coup) {
            return pile.is(net.minecraft.world.item.Items.BRUSH) ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        @Override
        protected void onRemove(BlockState etat, Level monde, BlockPos pos, BlockState nouvel, boolean deplace) {
            if (!monde.isClientSide && !nouvel.is(this)) Moteur.solDetruit(monde, pos);
            super.onRemove(etat, monde, pos, nouvel, deplace);
        }
    }

    // Kit de terrain : prélève les traces, affiche le dossier, puis sert d'instrument de pistage pendant la traque.
    public static class KitTerrain extends Item {
        public KitTerrain(Properties p) {
            super(p);
        }

        @Override
        public InteractionResult useOn(UseOnContext ctx) {
            if (ctx.getLevel().getBlockState(ctx.getClickedPos()).is(SOL)) {
                if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer j) {
                    Moteur.annonce(j, "sol_remue", 0xF2C93A);
                }
                return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
            }
            if (!ctx.getLevel().getBlockState(ctx.getClickedPos()).is(TRACE)) return InteractionResult.PASS;
            if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer j) Moteur.prelever(j, ctx.getClickedPos());
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }

        // Cible en vue mais pas encore identifiée : le clic droit se maintient, comme une longue-vue, pour la scanner.
        @Override
        public InteractionResultHolder<ItemStack> use(Level monde, Player joueur, InteractionHand main) {
            ItemStack pile = joueur.getItemInHand(main);
            boolean scan = !joueur.isShiftKeyDown() && (monde.isClientSide ? scanCoteClient : joueur instanceof ServerPlayer j && Moteur.enReperage(j));
            if (scan) {
                joueur.startUsingItem(main);
                return InteractionResultHolder.consume(pile);
            }
            if (!monde.isClientSide && joueur instanceof ServerPlayer j) Moteur.utiliserKit(j, j.isShiftKeyDown());
            return InteractionResultHolder.sidedSuccess(pile, monde.isClientSide);
        }

        @Override
        public int getUseDuration(ItemStack pile, net.minecraft.world.entity.LivingEntity porteur) {
            return 72000;
        }

        @Override
        public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack pile) {
            return net.minecraft.world.item.UseAnim.SPYGLASS;
        }

        @Override
        public void appendHoverText(ItemStack pile, TooltipContext ctx, List<Component> lignes, TooltipFlag drapeau) {
            lignes.add(Component.translatable("item.cobblemon_field_investigations.kit_terrain.aide1").withStyle(ChatFormatting.GRAY));
            lignes.add(Component.translatable("item.cobblemon_field_investigations.kit_terrain.aide2").withStyle(ChatFormatting.GRAY));
        }
    }

    // Échantillon : une fiole remplie sur une trace. On l'analyse avec le séquenceur de poche du kit (clic droit).
    public static class Echantillon extends Item {
        public Echantillon(Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level monde, Player joueur, InteractionHand main) {
            if (main == InteractionHand.MAIN_HAND && !monde.isClientSide && joueur instanceof ServerPlayer j) Moteur.analyser(j);
            return InteractionResultHolder.sidedSuccess(joueur.getItemInHand(main), monde.isClientSide);
        }

        @Override
        public Component getName(ItemStack pile) {
            CompoundTag d = donnees(pile);
            return Component.translatable(d.getBoolean("analyse") ? "cobblemon_field_investigations.echantillon.nom_analyse" : "cobblemon_field_investigations.echantillon.nom",
                    Component.translatable(Genetique.nomTrace(d.getString("genre"), d.getBoolean("enfoui"))));
        }

        @Override
        public void appendHoverText(ItemStack pile, TooltipContext ctx, List<Component> lignes, TooltipFlag drapeau) {
            CompoundTag d = donnees(pile);
            String genre = d.getString("genre");
            if (d.getBoolean("analyse")) {
                lignes.add(Component.translatable("cobblemon_field_investigations.echantillon.marqueur",
                        Component.translatable(Genetique.NOMS_MARQUEURS.getOrDefault(genre, "cobblemon_field_investigations.marqueur.type1")),
                        Genetique.libelle(genre, d.getString("valeur")).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.AQUA));
            } else {
                lignes.add(Component.translatable("cobblemon_field_investigations.echantillon.analyse",
                        Component.translatable(Genetique.nomJeu(Genetique.jeuAnalyse(genre, d.getString("jeu"))))).withStyle(ChatFormatting.AQUA));
                lignes.add(Component.translatable("cobblemon_field_investigations.echantillon.aide1").withStyle(ChatFormatting.GRAY));
                lignes.add(Component.translatable("cobblemon_field_investigations.echantillon.doses", d.getInt("doses")).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
