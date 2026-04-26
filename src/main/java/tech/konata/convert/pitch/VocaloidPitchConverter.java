package tech.konata.convert.pitch;

import tech.konata.convert.Note;
import tech.konata.convert.Pair;
import tech.konata.convert.Pitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts a relative {@link Pitch} curve into the two VOCALOID pitch-controller
 * event lists required by the {@code .vpr} format:
 *
 * <ul>
 *   <li><b>PIT</b> ({@code pitchBend}) – the pitch-bend value at each tick,
 *       normalised to the range {@code [-8191, 8191]}.</li>
 *   <li><b>PBS</b> ({@code pitchBendSens}) – the pitch-bend sensitivity (in
 *       semitones) at each tick; inserted only when the required range exceeds
 *       the hardware default of ±2 semitones.</li>
 * </ul>
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Obtain relative pitch data (semitone offsets from the current note).</li>
 *   <li>Split the data into <em>sections</em> separated by gaps of
 *       ≥ {@value #MIN_BREAK_TICKS} ticks (where there is nothing to express).</li>
 *   <li>For each section, compute the maximum absolute offset.  If it exceeds
 *       {@value #DEFAULT_PBS} semitones, emit a PBS event at the section start
 *       (and a reset event just after the section ends).</li>
 *   <li>Scale every relative value to {@code [-8191, 8191]} using the section's
 *       PBS range and emit a PIT event.</li>
 * </ol>
 */
public final class VocaloidPitchConverter {

    /** Maximum PIT value (14-bit unsigned half-range minus one). */
    private static final int  PITCH_MAX_VALUE   = 8191;

    /** Hardware-default pitch-bend sensitivity (±2 semitones). */
    private static final int  DEFAULT_PBS       = 2;

    /** Minimum tick gap between two pitch events to start a new section. */
    private static final long MIN_BREAK_TICKS   = 480L;

    /**
     * Radius (in ticks) used when obtaining relative pitch data.
     * Adds anchor points near note transitions to prevent unintended glides.
     */
    private static final long BORDER_APPEND_RADIUS = 5L;

    private VocaloidPitchConverter() { /* static utility class */ }

    /**
     * Container for the two VOCALOID pitch-controller event lists produced by
     * {@link #generateForVocaloid}.
     */
    public static final class VocaloidPartPitchData {

        private final long        startPos;
        private final List<Event> pit;
        private final List<Event> pbs;

        VocaloidPartPitchData(long startPos, List<Event> pit, List<Event> pbs) {
            this.startPos = startPos;
            this.pit      = Collections.unmodifiableList(pit);
            this.pbs      = Collections.unmodifiableList(pbs);
        }

        /** Tick offset of the containing part (always 0 for single-part projects). */
        public long getStartPos() { return startPos; }

        /** {@code pitchBend} controller events. */
        public List<Event> getPit() { return pit; }

        /** {@code pitchBendSens} controller events. */
        public List<Event> getPbs() { return pbs; }

        /** A single controller event: a tick position plus an integer value. */
        public static final class Event {
            private final long pos;
            private final int  value;

            Event(long pos, int value) {
                this.pos   = pos;
                this.value = value;
            }

            public long getPos()   { return pos;   }
            public int  getValue() { return value; }

            @Override
            public String toString() {
                return "Event{pos=" + pos + ", value=" + value + "}";
            }
        }
    }

    /**
     * Converts pitch automation data to VOCALOID PIT / PBS event lists.
     *
     * @param pitch source pitch data (absolute or relative)
     * @param notes note list that provides the key at each tick
     * @return the PIT and PBS event lists, or {@code null} if there is no
     *         meaningful pitch data
     */
    public static VocaloidPartPitchData generateForVocaloid(Pitch pitch, List<Note> notes) {
        List<Pair<Long, Double>> relativeData =
                PitchConverter.getRelativeData(pitch, notes, BORDER_APPEND_RADIUS);

        if (relativeData == null || relativeData.isEmpty()) return null;

        List<List<Pair<Long, Double>>> sections = splitIntoSections(relativeData);

        List<VocaloidPartPitchData.Event> pit = new ArrayList<>();
        List<VocaloidPartPitchData.Event> pbs = new ArrayList<>();

        for (List<Pair<Long, Double>> section : sections) {
            processPitchSection(section, pit, pbs);
        }

        return new VocaloidPartPitchData(0L, pit, pbs);
    }

    /**
     * Splits a flat list of pitch events into contiguous sections.
     * A new section begins whenever consecutive events are separated by
     * ≥ {@value #MIN_BREAK_TICKS} ticks.
     */
    private static List<List<Pair<Long, Double>>> splitIntoSections(
            List<Pair<Long, Double>> data) {

        List<List<Pair<Long, Double>>> sections = new ArrayList<>();
        long prevTick = Long.MIN_VALUE;

        for (Pair<Long, Double> event : data) {
            if (sections.isEmpty() || event.first - prevTick >= MIN_BREAK_TICKS) {
                sections.add(new ArrayList<>());
            }
            sections.get(sections.size() - 1).add(event);
            prevTick = event.first;
        }
        return sections;
    }

    /**
     * Processes one contiguous pitch section, appending events to {@code pit} and
     * {@code pbs}.
     *
     * <p>If the maximum absolute offset in the section exceeds {@value #DEFAULT_PBS}
     * semitones, a PBS override event is emitted at the section start and a PBS
     * reset event is emitted halfway through the trailing gap after the section.
     */
    private static void processPitchSection(
            List<Pair<Long, Double>> section,
            List<VocaloidPartPitchData.Event> pit,
            List<VocaloidPartPitchData.Event> pbs) {

        // Determine the minimum PBS needed to cover this section's range
        double maxAbsOffset = section.stream()
                .mapToDouble(e -> Math.abs(e.second))
                .max()
                .orElse(0.0);

        int sectionPbs = Math.max((int) Math.ceil(maxAbsOffset), DEFAULT_PBS);

        if (sectionPbs > DEFAULT_PBS) {
            long firstTick = section.get(0).first;
            long lastTick  = section.get(section.size() - 1).first;

            pbs.add(new VocaloidPartPitchData.Event(firstTick, sectionPbs));
            // Reset PBS to the hardware default once the section ends
            pbs.add(new VocaloidPartPitchData.Event(lastTick + MIN_BREAK_TICKS / 2L, DEFAULT_PBS));
        }

        // Emit PIT events, scaled and clamped to [-8191, 8191]
        for (Pair<Long, Double> event : section) {
            int rawValue   = (int) Math.round(event.second * PITCH_MAX_VALUE / sectionPbs);
            int clampedValue = Math.max(-PITCH_MAX_VALUE, Math.min(PITCH_MAX_VALUE, rawValue));
            pit.add(new VocaloidPartPitchData.Event(event.first, clampedValue));
        }
    }
}
