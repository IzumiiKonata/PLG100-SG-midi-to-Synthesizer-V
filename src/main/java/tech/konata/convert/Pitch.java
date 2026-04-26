package tech.konata.convert;

import java.util.Collections;
import java.util.List;

/**
 * Pitch automation data as a time series of {@code (tick, value)} pairs.
 *
 * <p>When {@code absolute} is {@code true}, values are absolute MIDI key numbers.
 * When {@code false}, values represent semitone offsets relative to the current note's key.
 */
public final class Pitch {

    private final List<Pair<Long, Double>> data;
    private final boolean absolute;

    public Pitch(List<Pair<Long, Double>> data, boolean absolute) {
        this.data = Collections.unmodifiableList(data);
        this.absolute = absolute;
    }

    /**
     * Returns the unmodifiable list of (tick, value) pitch points.
     */
    public List<Pair<Long, Double>> getData() {
        return data;
    }

    /**
     * Returns {@code true} if values are absolute MIDI key numbers;
     * {@code false} if they are semitone offsets relative to the current note.
     */
    public boolean isAbsolute() {
        return absolute;
    }

    @Override
    public String toString() {
        return "Pitch{absolute=" + absolute + ", points=" + data.size() + "}";
    }
}
