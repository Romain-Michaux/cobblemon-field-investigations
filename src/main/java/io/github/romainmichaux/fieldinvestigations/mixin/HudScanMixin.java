package io.github.romainmichaux.fieldinvestigations.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.romainmichaux.fieldinvestigations.client.FieldInvestigationsClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

// Pendant le scan, l'interface habituelle (barre d'objets, vie, viseur, équipe Pokémon, discussion...) laisse toute
// la place à la vue du scanner.
@Mixin(value = Gui.class, priority = 2000)
public class HudScanMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fieldinvestigations$scannerView(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        if (FieldInvestigationsClient.drawScanner(g)) ci.cancel();
    }
}
