package io.github.romainmichaux.fieldinvestigations.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

// Les bruitages propres au mod (assets/cobblemon_field_investigations/sounds, fabriqués par outils/gen_sons.py).
// Ce sont des sons d'interface, joués côté client seulement : ils n'ont pas besoin d'être enregistrés auprès du serveur.
final class Sounds {
    static final SoundEvent SIEVE = sound1("sieve"), PLASTER = sound1("plaster"), OVERFLOW = sound1("overflow"), TWEEZERS = sound1("tweezers"),
            PLUCK = sound1("pluck"), SHUTTER = sound1("shutter"), RUB = sound1("rub"), BRUSH_SOUND = sound1("brush"),
            VIAL = sound1("vial"), BUBBLE = sound1("bubble"), COEUR = sound1("heartbeat"), ROPE = sound1("rope"), CARABINER = sound1("carabiner"),
            BRANCH = sound1("branch"), CRACK = sound1("crack"), KNOCK = sound1("knock"), ROCKSLIDE = sound1("rockslide"),
            PICKAXE = sound1("pickaxe"), STONE = sound1("stone"), DROP = sound1("drop"), KNOB = sound1("knob"), ICE = sound1("ice"),
            FLAME = sound1("flame"), WHISTLE = sound1("whistle"), LAMP = sound1("lamp"), TICK = sound1("tick"), CLICK = sound1("click"),
            PAPER = sound1("paper"), SCAN = sound1("scan"), CHIME = sound1("chime"), SUCCESS = sound1("success"), FAILURE = sound1("failure"),
            IDENTIFIED = sound1("identified"), RANK_UP = sound1("rank_up");
    private Sounds() {}

    private static SoundEvent sound1(String name) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("cobblemon_field_investigations", name));
    }
}
