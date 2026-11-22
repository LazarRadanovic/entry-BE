package com.entry.fee;

/** How a cost is charged to the apartments it applies to. */
public enum CostType {
    /** Every apartment pays {@code amount}. */
    PER_APARTMENT,
    /** {@code amount} is a total, split proportionally to apartment area. */
    BY_AREA
}
