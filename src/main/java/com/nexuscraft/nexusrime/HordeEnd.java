package com.nexuscraft.nexusrime;

/** How a horde came to an end. */
enum HordeEnd {
    /** The walker was slain. */
    VICTORY,
    /** Time ran out, or the walker was lost: the cold simply fades. */
    FADED,
    /** The walker reached a protected place and the cold could go no further. */
    DISSOLVED,
    /** Winter ended while the horde was out. */
    SEASON_ENDED,
    /** No one left to hunt. */
    LOST_TARGET,
    /** An admin stopped it. */
    ADMIN,
    /** The server is stopping or the plugin is reloading. */
    SHUTDOWN
}
