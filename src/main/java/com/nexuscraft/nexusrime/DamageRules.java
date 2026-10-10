package com.nexuscraft.nexusrime;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/**
 * How much of a blow actually lands.
 *
 * <ul>
 *   <li>The walker takes real damage only from dragonglass gear (a multiple of the swing, so
 *   cooldown, crits and sharpness still count) or from fire (capped per hit so a lava pit is not
 *   an instant win). Anything else is cut to a sliver.</li>
 *   <li>Wights are weak to fire and shatter to the forged blade; everything else hurts them
 *   normally.</li>
 * </ul>
 */
final class DamageRules {

    private DamageRules() {
    }

    static double walkerDamage(RimeConfig cfg, DamageKind kind, double base) {
        return walkerDamage(cfg, kind, base, 1.0);
    }

    /** {@code potency} scales a forged blow: the blade is 1.0, lighter gear is less. */
    static double walkerDamage(RimeConfig cfg, DamageKind kind, double base, double potency) {
        double b = Math.max(0.0, base);
        return switch (kind) {
            case FORGED -> b * cfg.forgedWalkerMultiplier * Math.max(0.0, potency);
            case FIRE -> Math.min(b * cfg.walkerFireMultiplier, cfg.fireMaxPerHit);
            case ORDINARY -> b * cfg.walkerOrdinaryMultiplier;
        };
    }

    static double wightDamage(RimeConfig cfg, DamageKind kind, double base) {
        double b = Math.max(0.0, base);
        return switch (kind) {
            case FORGED -> b * cfg.forgedWightMultiplier;
            case FIRE -> b * cfg.wightFireMultiplier;
            case ORDINARY -> b;
        };
    }

    /** Classifies a blow that is not a shot (a swing, or a plain hit). */
    static DamageKind classify(DamageCause cause, boolean holdsForged) {
        return classify(cause, holdsForged, false);
    }

    /** Classifies a blow. {@code holdsForged} says the attacker is a player swinging dragonglass
     *  gear; {@code forgedShot} says the projectile that hit was fired with it. */
    static DamageKind classify(DamageCause cause, boolean holdsForged, boolean forgedShot) {
        if (cause == null) {
            return DamageKind.ORDINARY;
        }
        switch (cause) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR:
                return DamageKind.FIRE;
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK:
                return holdsForged ? DamageKind.FORGED : DamageKind.ORDINARY;
            case PROJECTILE:
                return forgedShot ? DamageKind.FORGED : DamageKind.ORDINARY;
            default:
                return DamageKind.ORDINARY;
        }
    }
}
