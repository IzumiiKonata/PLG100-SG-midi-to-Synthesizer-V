package tech.konata.convert.pitch;

import tech.konata.convert.Pair;
import tech.konata.convert.Tempo;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static tech.konata.convert.pitch.InterpolationUtils.interpolateCosineEaseInOut;
import static tech.konata.convert.pitch.InterpolationUtils.interpolateLinear;
import static tech.konata.convert.pitch.PitchConverter.DEFAULT_BPM;
import static tech.konata.convert.pitch.TickTimeTransformer.bpmToSecPerTick;

/**
 * Processes raw SVP pitch-delta points (including vibrato) into a final
 * {@code (tick, semitoneOffset)} list ready for conversion into relative pitch data.
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>Merge duplicate ticks by averaging their values.</li>
 *   <li>Interpolate between control points according to the curve mode.</li>
 *   <li>Compute and overlay vibrato for each note that carries vibrato
 *       parameters.</li>
 *   <li>Strip redundant consecutive identical values.</li>
 * </ol>
 */
public final class SvpPitchProcessor {

    private static final long   SAMPLING_INTERVAL_TICK           = 4L;
    private static final double DEFAULT_VIBRATO_START_SEC        = 0.25;
    private static final double DEFAULT_VIBRATO_EASE_IN_SEC      = 0.2;
    private static final double DEFAULT_VIBRATO_EASE_OUT_SEC     = 0.2;
    private static final double DEFAULT_VIBRATO_DEPTH_SEMITONE   = 1.0;
    private static final double DEFAULT_VIBRATO_FREQUENCY_HZ     = 5.5;
    private static final double DEFAULT_VIBRATO_PHASE_RAD        = 0.0;

    private SvpPitchProcessor() { /* static utility class */ }

    /**
     * Track-level default vibrato parameters, applied when a note's own vibrato
     * fields are {@code null}.
     */
    public static final class SvpDefaultVibratoParameters {
        public final Double vibratoStart;
        public final Double easeInLength;
        public final Double easeOutLength;
        public final Double depth;
        public final Double frequency;

        public SvpDefaultVibratoParameters(Double vibratoStart, Double easeInLength,
                Double easeOutLength, Double depth, Double frequency) {
            this.vibratoStart = vibratoStart;
            this.easeInLength = easeInLength;
            this.easeOutLength = easeOutLength;
            this.depth = depth;
            this.frequency = frequency;
        }
    }

    /**
     * A note together with its vibrato parameters.
     * All Double fields may be {@code null}, in which case the default value
     * (from {@link SvpDefaultVibratoParameters} or the class constant) is used.
     */
    public static final class SvpNoteWithVibrato {
        public final long   noteStartTick;
        public final long   noteLengthTick;
        public final Double vibratoStart;
        public final Double easeInLength;
        public final Double easeOutLength;
        public final Double depth;
        public final Double frequency;
        public final Double phase;

        public SvpNoteWithVibrato(long noteStartTick, long noteLengthTick,
                Double vibratoStart, Double easeInLength, Double easeOutLength,
                Double depth, Double frequency, Double phase) {
            this.noteStartTick = noteStartTick;
            this.noteLengthTick = noteLengthTick;
            this.vibratoStart = vibratoStart;
            this.easeInLength = easeInLength;
            this.easeOutLength = easeOutLength;
            this.depth = depth;
            this.frequency = frequency;
            this.phase = phase;
        }

        public long getNoteEndTick() {
            return noteStartTick + noteLengthTick;
        }
    }

    /**
     * Processes SVP input pitch-delta points into a final pitch list.
     *
     * @param points                 raw pitch-delta control points (semitone offsets)
     * @param interpolationMode      interpolation curve: {@code "linear"}, {@code "cosine"},
     *                               {@code "cubic"} (cubic falls back to cosine)
     * @param notesWithVibrato       per-note vibrato descriptors
     * @param tempos                 tempo map for tick↔sec conversions
     * @param vibratoEnvPoints       vibrato envelope control points (amplitude scale 0–1)
     * @param vibratoEnvMode         interpolation mode for the vibrato envelope
     * @param vibratoDefaultParams   track-level vibrato defaults (may be {@code null})
     * @return processed {@code (tick, semitoneOffset)} list
     */
    public static List<Pair<Long, Double>> processSvpInputPitchData(
            List<Pair<Long, Double>> points,
            String interpolationMode,
            List<SvpNoteWithVibrato> notesWithVibrato,
            List<Tempo> tempos,
            List<Pair<Long, Double>> vibratoEnvPoints,
            String vibratoEnvMode,
            SvpDefaultVibratoParameters vibratoDefaultParams) {

        // 1. Merge duplicate ticks, then interpolate pitch-delta
        List<Pair<Long, Double>> pitchInterpolated =
                interpolate(mergeDuplicateTicks(points), interpolationMode);
        if (pitchInterpolated == null) pitchInterpolated = Collections.emptyList();

        // 2. Merge and interpolate the vibrato envelope
        List<Pair<Long, Double>> envInterpolated =
                interpolate(mergeDuplicateTicks(vibratoEnvPoints), vibratoEnvMode);
        if (envInterpolated == null) envInterpolated = Collections.emptyList();

        // Flatten envelope into a tick→amplitude map for O(1) lookups
        Map<Long, Double> vibratoEnvMap = buildEnvelopeMap(envInterpolated);

        // 3. Overlay vibrato
        List<Pair<Long, Double>> withVibrato = appendVibrato(
                pitchInterpolated, notesWithVibrato, vibratoDefaultParams, tempos, vibratoEnvMap);

        // 4. Strip consecutive duplicates
        return removeConsecutiveDuplicates(withVibrato);
    }

    /**
     * Merges points that share the same tick by averaging their values.
     */
    static List<Pair<Long, Double>> mergeDuplicateTicks(List<Pair<Long, Double>> list) {
        return list.stream()
                .collect(Collectors.groupingBy(
                        Pair::getFirst,
                        Collectors.averagingDouble(Pair::getSecond)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new Pair<>(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Interpolates between control points using the specified curve mode.
     * Returns {@code null} if {@code points} is {@code null}.
     */
    static List<Pair<Long, Double>> interpolate(List<Pair<Long, Double>> points, String mode) {
        if (points == null) return null;
        switch (mode != null ? mode : "") {
            case "linear":
                return interpolateLinear(points, SAMPLING_INTERVAL_TICK);
            case "cosine":
            case "cubic":   // cubic is not yet implemented; fall through to cosine
            default:
                return interpolateCosineEaseInOut(points, SAMPLING_INTERVAL_TICK);
        }
    }

    /**
     * Converts an interpolated envelope list into a {@code tick → amplitude} map.
     */
    static Map<Long, Double> buildEnvelopeMap(List<Pair<Long, Double>> list) {
        Map<Long, Double> map = new HashMap<>(list.size() * 2);
        for (Pair<Long, Double> p : list) {
            map.put(p.first, p.second);
        }
        return map;
    }

    /**
     * Partitions the pitch data by note region (with inter-note gaps as null-note
     * regions), then overlays vibrato for each note region.
     */
    private static List<Pair<Long, Double>> appendVibrato(
            List<Pair<Long, Double>> data,
            List<SvpNoteWithVibrato> notes,
            SvpDefaultVibratoParameters defaultParams,
            List<Tempo> tempos,
            Map<Long, Double> vibratoEnv) {

        TickTimeTransformer timeTransformer = new TickTimeTransformer(tempos);

        // Build a list of (tick range → note or null) segments
        List<Pair<TickRange, SvpNoteWithVibrato>> segments = buildNoteSegments(notes);

        return segments.stream()
                .flatMap(segment -> {
                    List<Pair<Long, Double>> segmentData = data.stream()
                            .filter(p -> segment.first.contains(p.first))
                            .collect(Collectors.toList());
                    return appendVibratoForNote(
                            segmentData, segment.second,
                            defaultParams, timeTransformer, tempos, vibratoEnv).stream();
                })
                .collect(Collectors.toList());
    }

    /**
     * Builds the list of (range, note) segments, inserting null-note gap regions.
     */
    private static List<Pair<TickRange, SvpNoteWithVibrato>> buildNoteSegments(
            List<SvpNoteWithVibrato> notes) {

        List<Pair<TickRange, SvpNoteWithVibrato>> segments = new ArrayList<>();
        long lastEnd = 0L;

        for (SvpNoteWithVibrato note : notes) {
            if (lastEnd < note.noteStartTick) {
                segments.add(new Pair<>(new TickRange(lastEnd, note.noteStartTick), null));
            }
            segments.add(new Pair<>(new TickRange(note.noteStartTick, note.getNoteEndTick()), note));
            lastEnd = note.getNoteEndTick();
        }
        // Trailing gap (to +∞)
        segments.add(new Pair<>(new TickRange(lastEnd, Long.MAX_VALUE), null));
        return segments;
    }

    /**
     * Overlays vibrato onto pitch points within a single note region.
     * If {@code note} is {@code null}, the input points are returned unchanged.
     */
    private static List<Pair<Long, Double>> appendVibratoForNote(
            List<Pair<Long, Double>> points,
            SvpNoteWithVibrato note,
            SvpDefaultVibratoParameters defaultParams,
            TickTimeTransformer transformer,
            List<Tempo> tempos,
            Map<Long, Double> vibratoEnv) {

        if (note == null || note.noteStartTick < 0) return points;

        // --- Resolve vibrato parameters (note-level → default → fallback) ---
        double noteStartSec = transformer.tickToSec(note.noteStartTick);
        double noteEndSec   = transformer.tickToSec(note.getNoteEndTick());

        double vibratoStartSec = resolveParam(note.vibratoStart,
                defaultParams != null ? defaultParams.vibratoStart : null,
                DEFAULT_VIBRATO_START_SEC) + noteStartSec;

        double easeInSec = resolveParam(note.easeInLength,
                defaultParams != null ? defaultParams.easeInLength : null,
                DEFAULT_VIBRATO_EASE_IN_SEC);

        double easeOutSec = resolveParam(note.easeOutLength,
                defaultParams != null ? defaultParams.easeOutLength : null,
                DEFAULT_VIBRATO_EASE_OUT_SEC);

        // depth is halved: SVP depth is peak-to-peak; we use half-amplitude
        double depthSemitone = resolveParam(note.depth,
                defaultParams != null ? defaultParams.depth : null,
                DEFAULT_VIBRATO_DEPTH_SEMITONE) * 0.5;

        if (depthSemitone == 0.0) return points;

        double phaseRad    = note.phase != null ? note.phase : DEFAULT_VIBRATO_PHASE_RAD;
        double frequencyHz = resolveParam(note.frequency,
                defaultParams != null ? defaultParams.frequency : null,
                DEFAULT_VIBRATO_FREQUENCY_HZ);

        long vibratoStartTick = transformer.secToTick(vibratoStartSec);

        // Seconds-per-tick at the note's start (for phase increment calculation)
        double secPerTick = tempos.stream()
                .filter(t -> t.tickPosition <= note.noteStartTick)
                .reduce((a, b) -> b)
                .map(t -> bpmToSecPerTick(t.bpm))
                .orElse(bpmToSecPerTick(DEFAULT_BPM));

        // --- Build vibrato function: tick → semitone offset ---
        ToDoubleFunction<Long> vibratoFn = tick -> {
            double currentSec = transformer.tickToSec(tick);
            if (currentSec < vibratoStartSec) return 0.0;

            double easeIn  = Math.min(Math.max((currentSec - vibratoStartSec) / easeInSec, 0.0), 1.0);
            double easeOut = Math.min(Math.max((noteEndSec - currentSec)       / easeOutSec, 0.0), 1.0);
            double phase   = 2.0 * Math.PI * frequencyHz * (tick - vibratoStartTick) * secPerTick + phaseRad;
            double env     = vibratoEnv.getOrDefault(tick, 1.0);

            return env * depthSemitone * easeIn * easeOut * Math.sin(phase);
        };

        // --- Apply vibrato to each point, interpolating between them ---
        List<Pair<Long, Double>> basePoints = buildBasePoints(points, note);
        List<Pair<Long, Double>> result     = new ArrayList<>();
        Pair<Long, Double> lastPoint        = null;

        for (Pair<Long, Double> current : basePoints) {
            if (lastPoint != null) {
                long startTick = lastPoint.first + 1;
                long endTick   = current.first;
                for (long tick = startTick; tick < endTick; tick++) {
                    if ((tick - lastPoint.first) % SAMPLING_INTERVAL_TICK == 0) {
                        result.add(new Pair<>(tick, lastPoint.second + vibratoFn.applyAsDouble(tick)));
                    }
                }
            }
            result.add(new Pair<>(current.first, current.second + vibratoFn.applyAsDouble(current.first)));
            lastPoint = current;
        }
        return result;
    }

    /**
     * Returns the base pitch points for a note region. If the region is empty,
     * synthetic zero-offset points are created at the note boundaries. Also
     * ensures the last point is exactly at the note's end tick.
     */
    private static List<Pair<Long, Double>> buildBasePoints(
            List<Pair<Long, Double>> points, SvpNoteWithVibrato note) {

        List<Pair<Long, Double>> base = points.isEmpty()
                ? Arrays.asList(
                        new Pair<>(note.noteStartTick, 0.0),
                        new Pair<>(note.getNoteEndTick(), 0.0))
                : new ArrayList<>(points);

        if (base.get(base.size() - 1).first != note.getNoteEndTick()) {
            if (!(base instanceof ArrayList)) base = new ArrayList<>(base);
            base.add(new Pair<>(note.getNoteEndTick(), base.get(base.size() - 1).second));
        }
        return base;
    }

    /**
     * Removes consecutive points that share the same value, keeping only the
     * first occurrence of each run.
     */
    public static List<Pair<Long, Double>> removeConsecutiveDuplicates(
            List<Pair<Long, Double>> points) {

        List<Pair<Long, Double>> result = new ArrayList<>();
        Double lastValue = null;

        for (Pair<Long, Double> p : points) {
            if (!Objects.equals(p.second, lastValue)) {
                result.add(p);
                lastValue = p.second;
            }
        }
        return result;
    }

    private static double resolveParam(Double noteValue, Double defaultValue, double fallback) {
        if (noteValue  != null) return noteValue;
        if (defaultValue != null) return defaultValue;
        return fallback;
    }

    static final class TickRange {
        final long start;
        final long end;

        TickRange(long start, long end) {
            this.start = start;
            this.end   = end;
        }

        boolean contains(long tick) {
            return tick >= start && tick < end;
        }
    }
}
