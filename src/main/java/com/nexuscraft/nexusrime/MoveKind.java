package com.nexuscraft.nexusrime;

/** One kind of step in a route. */
enum MoveKind {
    /** An ordinary step: flat, up one block, or down a few. */
    WALK,
    /** A step through water. */
    SWIM,
    /** A closed door in the way: smash it open. */
    BREACH,
    /** Liquid underfoot: freeze it into a bridge first. */
    BRIDGE,
    /** A wall too tall to step up: the dead pile on each other to climb it. */
    CLIMB
}
