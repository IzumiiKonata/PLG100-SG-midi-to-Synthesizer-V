package tech.konata.convert;

/**
 * A tempo change event, specifying BPM at a given tick position.
 */
public final class Tempo {

    /** Tick at which this tempo takes effect. */
    public final long tickPosition;

    /** Beats per minute. */
    public final double bpm;

    public Tempo(long tickPosition, double bpm) {
        if (tickPosition < 0) {
            throw new IllegalArgumentException("Tick position must be non-negative");
        }
        if (bpm <= 0) {
            throw new IllegalArgumentException("BPM must be positive, got: " + bpm);
        }
        this.tickPosition = tickPosition;
        this.bpm = bpm;
    }

    @Override
    public String toString() {
        return "Tempo{tick=" + tickPosition + ", bpm=" + bpm + "}";
    }
}
