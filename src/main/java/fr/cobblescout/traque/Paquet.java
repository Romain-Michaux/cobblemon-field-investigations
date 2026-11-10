package fr.cobblescout.traque;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Un seul type de message dans les deux sens : un petit dictionnaire, dont la clé "t" dit de quoi il s'agit.
public record Paquet(CompoundTag donnees) implements CustomPacketPayload {
    public static final Type<Paquet> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", "traque"));
    public static final StreamCodec<FriendlyByteBuf, Paquet> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeNbt(p.donnees), buf -> new Paquet(buf.readNbt()));

    public static Paquet de(String type) {
        CompoundTag t = new CompoundTag();
        t.putString("t", type);
        return new Paquet(t);
    }

    public String genre() {
        return donnees.getString("t");
    }

    @Override
    public Type<Paquet> type() {
        return TYPE;
    }
}
