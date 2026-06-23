package com.cobblemonbridge.mixin;

import com.cobblemon.mod.common.api.abilities.Ability;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes a Pokémon adopt its Mega form's ability when it Mega Evolves, even if its current ability
 * was force-set (e.g. Mega Gengar should become Shadow Tag).
 *
 * <p>mega_showdown mega-evolves by toggling Cobblemon's {@code mega} aspect, which drives
 * {@code Pokemon.setForm(megaForm)} → {@code attemptAbilityUpdate()}. But that method early-returns
 * when the current ability's {@code forced} flag is set, so a forced base ability (Cursed Body)
 * survives the form change and the Mega form's ability is never applied. The Mega form data is
 * correct ({@code gengar.json} Mega → {@code ["shadowtag","h:shadowtag"]}); only the application is
 * blocked.
 *
 * <p>We inject at the HEAD of {@code attemptAbilityUpdate} — which Cobblemon calls <em>after</em>
 * {@code this.form} is already the new form — and, if the current form is a Mega form, clear the
 * {@code forced} flag so Cobblemon's own re-resolution runs and selects the Mega form's ability by
 * the existing priority+index mapping (so hidden-ability slots map to the Mega's {@code h:} entry).
 *
 * <p>General to all Megas: Mega / Mega-X / Mega-Y forms all carry the lowercased {@code "mega"}
 * label (the X/Y aspect is {@code mega_x}/{@code mega_y}, so we key on the label, not the aspect).
 * On un-mega the form setter calls this again with the base form (no {@code "mega"} label), so we
 * no-op and Cobblemon re-resolves the base ability normally. Non-Mega forced abilities are never
 * touched. The bridge is server-only, so this only runs server-side; the resolved ability syncs to
 * clients through Cobblemon's normal ability-update packet.
 */
@Mixin(Pokemon.class)
public abstract class MegaAbilityMixin {

    @Inject(method = "attemptAbilityUpdate", at = @At("HEAD"), remap = false)
    private void cobblemonbridge$unforceMegaAbility(CallbackInfo ci) {
        Pokemon self = (Pokemon) (Object) this;
        Ability ability = self.getAbility();
        if (ability == null || !ability.getForced()) {
            return; // not forced -> Cobblemon's own logic already re-resolves the ability
        }
        FormData form = self.getForm();
        if (form != null && form.getLabels().contains("mega")) {
            ability.setForced$common(false);
        }
    }
}
