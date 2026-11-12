package io.github.romainmichaux.fieldinvestigations.mixin;

import java.util.Map;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Items and blocks had French ids up to 1.1.0. Worlds saved with those builds still hold them in inventories and
// chunks, so every registry lookup of an old id is redirected to the new one; nothing is lost on update.
@Mixin(MappedRegistry.class)
public abstract class LegacyIdsMixin {
    private static final String NAMESPACE = "cobblemon_field_investigations";
    private static final Map<String, String> OLD_IDS = Map.of(
            "kit_terrain", "field_kit",
            "fiole", "vial",
            "echantillon", "sample",
            "sol_remue", "disturbed_soil");

    private static ResourceLocation fieldinvestigations$current(ResourceLocation id) {
        if (id == null || !NAMESPACE.equals(id.getNamespace())) return id;
        String path = OLD_IDS.get(id.getPath());
        return path == null ? id : ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    private static <T> ResourceKey<T> fieldinvestigations$current(ResourceKey<T> key) {
        if (key == null) return null;
        ResourceLocation id = fieldinvestigations$current(key.location());
        if (id == key.location()) return key;
        ResourceKey<? extends Registry<T>> registry = ResourceKey.createRegistryKey(key.registry());
        return ResourceKey.create(registry, id);
    }

    @ModifyVariable(method = {"get(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/Object;",
            "getHolder(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            "containsKey(Lnet/minecraft/resources/ResourceLocation;)Z"},
            at = @At("HEAD"), argsOnly = true)
    private ResourceLocation fieldinvestigations$oldLocation(ResourceLocation id) {
        return fieldinvestigations$current(id);
    }

    @ModifyVariable(method = {"get(Lnet/minecraft/resources/ResourceKey;)Ljava/lang/Object;",
            "getHolder(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;",
            "containsKey(Lnet/minecraft/resources/ResourceKey;)Z"},
            at = @At("HEAD"), argsOnly = true)
    private ResourceKey<?> fieldinvestigations$oldKey(ResourceKey<?> key) {
        return fieldinvestigations$current(key);
    }
}
