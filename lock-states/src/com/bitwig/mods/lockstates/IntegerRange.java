package com.bitwig.mods.lockstates;

/** Integer parameter conversion without the controller target's signed-int cardinality arithmetic. */
public record IntegerRange(int minimum, int maximum) {
    public IntegerRange {
        if (maximum < minimum) throw new IllegalArgumentException("Reversed integer parameter range");
    }

    public long steps() { return (long) maximum - minimum + 1; }

    public double normalized(double raw) {
        if (!Double.isFinite(raw) || raw != Math.rint(raw) || raw < minimum || raw > maximum) {
            throw new IllegalArgumentException("Integer parameter value is outside its declared range");
        }
        return minimum == maximum ? 0 : (raw - minimum) / ((double) maximum - minimum);
    }

    public double raw(double normalized) {
        if (!Double.isFinite(normalized) || normalized < 0 || normalized > 1) {
            throw new IllegalArgumentException("Invalid normalized integer parameter value");
        }
        long rounded = Math.round(minimum + normalized * ((double) maximum - minimum));
        return Math.max((long) minimum, Math.min((long) maximum, rounded));
    }
}
