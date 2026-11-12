package io.github.romainmichaux.fieldinvestigations;

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

// The mod's blocks and items: traces and disturbed soil in the world, field kit, vials and samples.
public final class ModContent {
    public static final String NS = "cobblemon_field_investigations";
    public static Block TRACE, DISTURBED_SOIL;
    public static Item KIT, VIAL, SAMPLE;
    // Set by the client on every case update: true while the target is waiting to be scanned.
    public static volatile boolean clientScan;

    private ModContent() {}

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NS, path);
    }

    static void register() {
        TRACE = Registry.register(BuiltInRegistries.BLOCK, id("trace"), new TraceBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE).noCollission().instabreak().noLootTable().noOcclusion().sound(SoundType.MOSS_CARPET)
                .pushReaction(PushReaction.DESTROY)));
        DISTURBED_SOIL = Registry.register(BuiltInRegistries.BLOCK, id("disturbed_soil"), new DisturbedSoilBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIRT).strength(0.8f).noLootTable().sound(SoundType.GRAVEL)));
        KIT = Registry.register(BuiltInRegistries.ITEM, id("field_kit"), new FieldKitItem(new Item.Properties().stacksTo(1)));
        VIAL = Registry.register(BuiltInRegistries.ITEM, id("vial"), new Item(new Item.Properties().stacksTo(16)));
        SAMPLE = Registry.register(BuiltInRegistries.ITEM, id("sample"), new SampleItem(new Item.Properties().stacksTo(1)));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> {
            e.accept(KIT);
            e.accept(VIAL);
        });
    }

    static CompoundTag data(ItemStack stack) {
        return Legacy.item(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
    }

    // Trace left by the Pokémon: a flat decal sampled with the field kit.
    public static class TraceBlock extends Block implements net.minecraft.world.level.block.SimpleWaterloggedBlock {
        // True when the trace lies underwater (aquatic cases): the water stays in place around it.
        public static final net.minecraft.world.level.block.state.properties.BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
        // 0 to 4: the five surface traces; 5 to 12: clues dug out of the ground (bone, shells, fang, tooth, feather, shed skin, claw, scale).
        public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 12);
        // UP = lying on the ground (or on leaves); otherwise pressed against the block behind it (trunk, cliff).
        public static final DirectionProperty FACING = BlockStateProperties.FACING;
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 2, 15);
        private static final VoxelShape NORTH_DIR = Block.box(1, 1, 14, 15, 15, 16), SOUTH_DIR = Block.box(1, 1, 0, 15, 15, 2);
        private static final VoxelShape EAST_DIR = Block.box(0, 1, 1, 2, 15, 15), WEST_DIR = Block.box(14, 1, 1, 16, 15, 15);

        public TraceBlock(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(KIND, 0).setValue(FACING, Direction.UP).setValue(WATERLOGGED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(KIND, FACING, WATERLOGGED);
        }

        @Override
        protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
            return state.getValue(WATERLOGGED) ? net.minecraft.world.level.material.Fluids.WATER.getSource(false) : super.getFluidState(state);
        }

        @Override
        protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
            if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, net.minecraft.world.level.material.Fluids.WATER, net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
            return super.updateShape(state, dir, neighbour, level, pos, neighbourPos);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return switch (state.getValue(FACING)) {
                case NORTH -> NORTH_DIR;
                case SOUTH -> SOUTH_DIR;
                case EAST -> EAST_DIR;
                case WEST -> WEST_DIR;
                default -> SHAPE;
            };
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide && player instanceof ServerPlayer j) {
                Messages.announce(j, "kit_required", 0xF2C93A);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    // Disturbed soil: a clue is buried in it. It is brushed clear like suspicious sand; breaking it destroys the clue.
    public static class DisturbedSoilBlock extends Block {
        public static final IntegerProperty MATERIAL = IntegerProperty.create("material", 0, 2); // dirt, sand, gravel
        public static final IntegerProperty UNCOVERED = IntegerProperty.create("uncovered", 0, 3);

        public DisturbedSoilBlock(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(MATERIAL, 0).setValue(UNCOVERED, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(MATERIAL, UNCOVERED);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide && player instanceof ServerPlayer j) {
                Messages.announce(j, "disturbed_soil", 0xF2C93A);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Brush in hand: let the item act (otherwise the message above would swallow the click and brushing would never start).
        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            return stack.is(net.minecraft.world.item.Items.BRUSH) ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
            if (!level.isClientSide && !newState.is(this)) Traces.groundDestroyed(level, pos);
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    // Field kit: samples traces, shows the case file, then serves as the tracking instrument during the hunt.
    public static class FieldKitItem extends Item {
        public FieldKitItem(Properties p) {
            super(p);
        }

        @Override
        public InteractionResult useOn(UseOnContext ctx) {
            if (ctx.getLevel().getBlockState(ctx.getClickedPos()).is(DISTURBED_SOIL)) {
                if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer j) {
                    Messages.announce(j, "disturbed_soil", 0xF2C93A);
                }
                return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
            }
            if (!ctx.getLevel().getBlockState(ctx.getClickedPos()).is(TRACE)) return InteractionResult.PASS;
            if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer j) Samples.takeSample(j, ctx.getClickedPos());
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }

        // Target in sight but not yet identified: right-click is held, like a spyglass, to scan it.
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            boolean scan = !player.isShiftKeyDown() && (level.isClientSide ? clientScan : player instanceof ServerPlayer j && Tracking.inSpotting(j));
            if (scan) {
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(stack);
            }
            if (!level.isClientSide && player instanceof ServerPlayer j) Tracking.useKit(j, j.isShiftKeyDown());
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity user) {
            return 72000;
        }

        @Override
        public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
            return net.minecraft.world.item.UseAnim.SPYGLASS;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("item.cobblemon_field_investigations.field_kit.help1").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("item.cobblemon_field_investigations.field_kit.help2").withStyle(ChatFormatting.GRAY));
        }
    }

    // Sample: a vial filled from a trace. Analysed with the kit's pocket sequencer (right-click).
    public static class SampleItem extends Item {
        public SampleItem(Properties p) {
            super(p);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (hand == InteractionHand.MAIN_HAND && !level.isClientSide && player instanceof ServerPlayer j) Samples.analyse(j);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }

        @Override
        public Component getName(ItemStack stack) {
            CompoundTag d = data(stack);
            return Component.translatable(d.getBoolean("analyse") ? "cobblemon_field_investigations.sample.analysed_name" : "cobblemon_field_investigations.sample.name",
                    Component.translatable(Genetics.traceName(d.getString("kind"), d.getBoolean("buried"), d.getInt("variant"))));
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
            CompoundTag d = data(stack);
            String kind = d.getString("kind");
            if (d.getBoolean("analyse")) {
                lines.add(Component.translatable("cobblemon_field_investigations.sample.marker",
                        Component.translatable(Genetics.MARKER_NAMES.getOrDefault(kind, "cobblemon_field_investigations.marker.type1")),
                        Genetics.label(kind, d.getString("value")).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.AQUA));
            } else {
                lines.add(Component.translatable("cobblemon_field_investigations.sample.analyse",
                        Component.translatable(Genetics.gameName(Genetics.analysisGame(kind, d.getString("game"))))).withStyle(ChatFormatting.AQUA));
                lines.add(Component.translatable("cobblemon_field_investigations.sample.help1").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("cobblemon_field_investigations.sample.doses", d.getInt("doses")).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
