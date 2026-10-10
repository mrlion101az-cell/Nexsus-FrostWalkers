package com.nexuscraft.nexusrime;

/** Who the horde may hunt: a living, playing person standing outside every protected place. */
final class TargetRules {

    private TargetRules() {
    }

    static boolean eligible(TargetFacts f) {
        return f.online() && f.alive() && f.inHordeWorld() && f.playableMode() && !f.inProtectedPlace();
    }
}
