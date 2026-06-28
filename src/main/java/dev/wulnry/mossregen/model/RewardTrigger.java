package dev.wulnry.mossregen.model;

/**
 * Global grup hediyelerinin ne zaman verilecegini belirler.
 */
public enum RewardTrigger {
    /** Gruptaki her bitki kirildiginda sans denenir. */
    ON_BREAK,
    /** Grup toplu halde yenilendiginde, kiran herkese sans denenir. */
    ON_GROUP_REGEN
}
