package io.github.romainmichaux.fieldinvestigations;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Un seul type de message dans les deux sens : un petit dictionnaire, dont la clé "t" dit de quoi il s'agit.
public record Packet(CompoundTag data) implements CustomPacketPayload {
    public static final Type<Packet> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", "hunt"));
    public static final StreamCodec<FriendlyByteBuf, Packet> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeNbt(p.data), buf -> new Packet(buf.readNbt()));

    public static Packet from(String type) {
        CompoundTag t = new CompoundTag();
        t.putString("t", type);
        return new Packet(t);
    }

    public String kind() {
        return data.getString("t");
    }

    @Override
    public Type<Packet> type() {
        return TYPE;
    }
}
