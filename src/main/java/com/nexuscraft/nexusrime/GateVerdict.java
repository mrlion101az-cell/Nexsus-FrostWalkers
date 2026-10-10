package com.nexuscraft.nexusrime;

/** Why a horde may or may not begin right now. The first rule that applies wins. */
enum GateVerdict {
    OK,
    DISABLED,
    WORLD_NOT_ALLOWED,
    WRONG_SEASON,
    SEASON_UNKNOWN,
    TOO_MANY_ACTIVE,
    COOLDOWN,
    NOT_ENOUGH_PLAYERS,
    NO_TARGET,
    NO_ROLL,
    NO_SPAWN_SITE,
    VETOED
}
