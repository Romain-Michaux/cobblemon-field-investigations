package io.github.romainmichaux.fieldinvestigations.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.romainmichaux.fieldinvestigations.client.FieldInvestigationsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

// Pendant le scan au kit de terrain, le champ de vision se resserre, comme avec une longue-vue.
@Mixin(AbstractClientPlayer.class)
public class ZoomScanMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void fieldinvestigations$scanZoom(CallbackInfoReturnable<Float> circ) {
        if ((Object) this == Minecraft.getInstance().player && FieldInvestigationsClient.scanActive()) circ.setReturnValue(circ.getReturnValueF() * 0.35f);
    }
}
