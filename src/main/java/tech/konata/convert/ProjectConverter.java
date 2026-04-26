package tech.konata.convert;

/**
 * Abstract base for converters that produce a specific singing-synthesis project format
 * (e.g. Synthesizer V {@code .svp}, VOCALOID {@code .vpr}).
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #load()} — initialise internal state / load templates</li>
 *   <li>{@link #insertTempo(long, double)} — add tempo events (in tick order)</li>
 *   <li>{@link #insertNote(String, long, long, int)} — add note events (in tick order)</li>
 *   <li>{@link #onPitchBend(int, long)} — accumulate raw MIDI pitch-bend events</li>
 *   <li>{@link #save(String)} — finalise and write the project file</li>
 * </ol>
 */
public abstract class ProjectConverter {

    /**
     * Initialises the converter and loads any required templates or resources.
     */
    public abstract void load();

    /**
     * Inserts a tempo change event.
     *
     * @param tick tick position of the tempo change
     * @param bpm  beats per minute (must be positive)
     */
    public abstract void insertTempo(long tick, double bpm);

    /**
     * Inserts a note with the given lyric syllable.
     *
     * @param lyric     lyric syllable (hiragana, romaji, etc.)
     * @param tickStart tick at which the note begins (inclusive)
     * @param tickEnd   tick at which the note ends (exclusive)
     * @param midiKey   MIDI note number (0–127)
     */
    public abstract void insertNote(String lyric, long tickStart, long tickEnd, int midiKey);

    /**
     * Records a raw MIDI pitch-bend event. The default implementation is a no-op;
     * override in converters that support pitch-bend output.
     *
     * @param value 14-bit signed pitch-bend value in the range {@code [-8192, 8191]}
     * @param tick  tick position of the event
     */
    public void onPitchBend(int value, long tick) {
        // no-op by default
    }

    /**
     * Finalises the project and writes it to disk.
     *
     * @param baseName output file base name (without extension)
     */
    public abstract void save(String baseName);
}
