package com.nexuscraft.nexusrime;

/** What decides whether a player can be hunted. */
record TargetFacts(boolean online, boolean alive, boolean inHordeWorld, boolean playableMode, boolean inProtectedPlace) {
}
