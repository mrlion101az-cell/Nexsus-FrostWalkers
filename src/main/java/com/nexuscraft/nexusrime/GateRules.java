package com.nexuscraft.nexusrime;

/**
 * The single place that decides whether a horde may begin. First matching rule wins, in this order:
 * disabled, wrong world, season, too many active, cooldown, too few players, nobody to hunt, dice.
 *
 * <p>An admin-forced start skips the season, cooldown, player-count and dice rules (so an event can
 * be tested in summer) but never the on/off switch, the world list, the active-horde limit, or the
 * need for someone to hunt.
 */
final class GateRules {

    private GateRules() {
    }

    static GateVerdict evaluate(RimeConfig cfg, GateFacts f) {
        if (!cfg.enabled) {
            return GateVerdict.DISABLED;
        }
        if (!worldAllowed(cfg, f.worldName())) {
            return GateVerdict.WORLD_NOT_ALLOWED;
        }
        if (!f.forced() && cfg.seasonRequired) {
            if (f.season() == SeasonStatus.NOT_WINTER) {
                return GateVerdict.WRONG_SEASON;
            }
            if (f.season() == SeasonStatus.UNKNOWN && !cfg.allowWhenSeasonsMissing) {
                return GateVerdict.SEASON_UNKNOWN;
            }
        }
        if (f.activeHordes() >= cfg.maxConcurrent) {
            return GateVerdict.TOO_MANY_ACTIVE;
        }
        if (!f.forced() && f.lastEventMillis() > 0 && f.nowMillis() - f.lastEventMillis() < cfg.cooldownMinutes * 60_000L) {
            return GateVerdict.COOLDOWN;
        }
        if (!f.forced() && f.eligiblePlayers() < cfg.minPlayers) {
            return GateVerdict.NOT_ENOUGH_PLAYERS;
        }
        if (!f.hasTarget()) {
            return GateVerdict.NO_TARGET;
        }
        if (!f.forced() && f.rollPercent() >= cfg.chancePercent) {
            return GateVerdict.NO_ROLL;
        }
        return GateVerdict.OK;
    }

    static boolean worldAllowed(RimeConfig cfg, String worldName) {
        if (worldName == null) {
            return false;
        }
        for (String w : cfg.worlds) {
            if (w.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    /** Minutes left before the cooldown allows another event, or 0. */
    static long cooldownMinutesLeft(RimeConfig cfg, long nowMillis, long lastEventMillis) {
        if (lastEventMillis <= 0) {
            return 0;
        }
        long left = cfg.cooldownMinutes * 60_000L - (nowMillis - lastEventMillis);
        return left <= 0 ? 0 : (left + 59_999L) / 60_000L;
    }
}
