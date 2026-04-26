package tech.konata.convert;

/**
 * Represents a single musical note with a MIDI pitch key, tick range, and lyric syllable.
 */
public final class Note {

    /** MIDI note number (0–127). */
    private final int key;

    /** Tick position where the note starts. */
    private final long tickOn;

    /** Tick position where the note ends (exclusive). */
    private final long tickOff;

    /** Lyric syllable assigned to this note. */
    private final String lyric;

    public Note(int key, long tickOn, long tickOff, String lyric) {
        if (key < 0 || key > 127) {
            throw new IllegalArgumentException("MIDI key must be in [0, 127], got: " + key);
        }
        if (tickOn < 0 || tickOff < 0) {
            throw new IllegalArgumentException("Tick values must be non-negative");
        }
        if (tickOff < tickOn) {
            throw new IllegalArgumentException(
                "tickOff (" + tickOff + ") must be greater than tickOn (" + tickOn + ")");
        }
        this.key = key;
        this.tickOn = tickOn;
        this.tickOff = tickOff;
        this.lyric = lyric == null ? "" : lyric;
    }

    public int getKey() {
        return key;
    }

    public long getTickOn() {
        return tickOn;
    }

    public long getTickOff() {
        return tickOff;
    }

    public long getDurationTicks() {
        return tickOff - tickOn;
    }

    public String getLyric() {
        return lyric;
    }

    @Override
    public String toString() {
        return "Note{key=" + key + ", on=" + tickOn + ", off=" + tickOff + ", lyric='" + lyric + "'}";
    }
}
