package com.nexuscraft.nexusrime;

/** What decides how much frost a player takes this moment. */
record ColdFacts(double distanceToWalker, int leatherPieces, boolean nearWarmth) {
}
