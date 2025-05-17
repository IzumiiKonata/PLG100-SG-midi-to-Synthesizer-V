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

public class SvpPitchProcessor {
    // region Constants
    private static final long SAMPLING_INTERVAL_TICK = 4L;
    private static final double SVP_VIBRATO_DEFAULT_START_SEC = 0.25;
    private static final double SVP_VIBRATO_DEFAULT_EASE_IN_SEC = 0.2;
    private static final double SVP_VIBRATO_DEFAULT_EASE_OUT_SEC = 0.2;
    private static final double SVP_VIBRATO_DEFAULT_DEPTH_SEMITONE = 1.0;
    private static final double SVP_VIBRATO_DEFAULT_FREQUENCY_HZ = 5.5;
    private static final double SVP_VIBRATO_DEFAULT_PHASE_RAD = 0.0;
    // endregion

    // region Data classes
    public static class SvpDefaultVibratoParameters {
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

    public static class SvpNoteWithVibrato {
        public final long noteStartTick;
        public final long noteLengthTick;
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
    // endregion

    // region Main processing
    public static List<Pair<Long, Double>> processSvpInputPitchData(
            List<Pair<Long, Double>> points,
            String mode,
            List<SvpNoteWithVibrato> notesWithVibrato,
            List<Tempo> tempos,
            List<Pair<Long, Double>> vibratoEnvPoints,
            String vibratoEnvMode,
            SvpDefaultVibratoParameters vibratoDefaultParameters) {

        List<Pair<Long, Double>> merged = merge(points);
        List<Pair<Long, Double>> interpolated = interpolate(merged, mode);
        if (interpolated == null) interpolated = Collections.emptyList();

        List<Pair<Long, Double>> envInterpolated = interpolate(
                merge(vibratoEnvPoints), 
                vibratoEnvMode
        );
        if (envInterpolated == null) envInterpolated = Collections.emptyList();

        Map<Long, Double> vibratoEnv = extendEveryTick(envInterpolated);

        List<Pair<Long, Double>> withVibrato = appendVibrato(
                interpolated,
                notesWithVibrato,
                vibratoDefaultParameters,
                tempos,
                vibratoEnv
        );

        return removeRedundantPoints(withVibrato);
    }
    // endregion

    // region Helper methods
    public static List<Pair<Long, Double>> merge(List<Pair<Long, Double>> list) {
        return list.stream()
                .collect(Collectors.groupingBy(
                        Pair::getFirst,
                        Collectors.averagingDouble(Pair::getSecond)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new Pair<>(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public static List<Pair<Long, Double>> interpolate(
            List<Pair<Long, Double>> points, 
            String mode) {
        if (points == null) return null;

        switch (mode != null ? mode : "") {
            case "linear":
                return interpolateLinear(points, SAMPLING_INTERVAL_TICK);
            case "cosine":
            case "cubic": // TODO: Implement cubic
                return interpolateCosineEaseInOut(points, SAMPLING_INTERVAL_TICK);
            default:
                return interpolateCosineEaseInOut(points, SAMPLING_INTERVAL_TICK);
        }
    }

    public static Map<Long, Double> extendEveryTick(List<Pair<Long, Double>> list) {
        Map<Long, Double> map = new HashMap<>();
        for (Pair<Long, Double> p : list) {
            map.put(p.first, p.second);
        }
        return map;
    }

    private static List<Pair<Long, Double>> appendVibrato(
            List<Pair<Long, Double>> data,
            List<SvpNoteWithVibrato> notes,
            SvpDefaultVibratoParameters defaultParams,
            List<Tempo> tempos,
            Map<Long, Double> vibratoEnv) {

        TickTimeTransformer transformer = new TickTimeTransformer(tempos);

        List<Pair<LongRange, SvpNoteWithVibrato>> ranges = new ArrayList<>();
        long lastEnd = 0L;
        for (SvpNoteWithVibrato note : notes) {
            if (lastEnd < note.noteStartTick) {
                ranges.add(new Pair<>(new LongRange(lastEnd, note.noteStartTick), null));
            }
            ranges.add(new Pair<>(
                new LongRange(note.noteStartTick, note.getNoteEndTick()),
                note
            ));
            lastEnd = note.getNoteEndTick();
        }
        ranges.add(new Pair<>(new LongRange(lastEnd, Long.MAX_VALUE), null));

        return ranges.stream()
                .flatMap(p -> appendVibratoInNote(
                        data.stream()
                                .filter(point -> p.first.contains(point.first))
                                .collect(Collectors.toList()),
                        p.second,
                        defaultParams,
                        transformer,
                        tempos,
                        vibratoEnv
                ).stream())
                .collect(Collectors.toList());
    }

    // region Vibrato Implementation
    private static List<Pair<Long, Double>> appendVibratoInNote(
            List<Pair<Long, Double>> points,
            SvpNoteWithVibrato note,
            SvpDefaultVibratoParameters defaultParams,
            TickTimeTransformer transformer,
            List<Tempo> tempos,
            Map<Long, Double> vibratoEnv) {

        if (note == null || note.noteStartTick < 0) return points;

        // region Parameter Resolution
        final double noteStartSec = transformer.tickToSec(note.noteStartTick);
        final double noteEndSec = transformer.tickToSec(note.getNoteEndTick());

        final double vibratoStartSec = resolveParameter(
                note.vibratoStart,
                defaultParams != null ? defaultParams.vibratoStart : null,
                SVP_VIBRATO_DEFAULT_START_SEC
        ) + noteStartSec;

        final double easeInSec = resolveParameter(
                note.easeInLength,
                defaultParams != null ? defaultParams.easeInLength : null,
                SVP_VIBRATO_DEFAULT_EASE_IN_SEC
        );

        final double easeOutSec = resolveParameter(
                note.easeOutLength,
                defaultParams != null ? defaultParams.easeOutLength : null,
                SVP_VIBRATO_DEFAULT_EASE_OUT_SEC
        );

        final double depthSemitone = resolveParameter(
                note.depth,
                defaultParams != null ? defaultParams.depth : null,
                SVP_VIBRATO_DEFAULT_DEPTH_SEMITONE
        ) * 0.5;

        if (depthSemitone == 0.0) return points;

        final double phaseRad = note.phase != null ?
                note.phase : SVP_VIBRATO_DEFAULT_PHASE_RAD;

        final double frequencyHz = resolveParameter(
                note.frequency,
                defaultParams != null ? defaultParams.frequency : null,
                SVP_VIBRATO_DEFAULT_FREQUENCY_HZ
        );

        final long vibratoStartTick = transformer.secToTick(vibratoStartSec);
        // endregion

        // region Vibrato Calculation
        final double secPerTick = tempos.stream()
                .filter(t -> t.tickPosition <= note.noteStartTick)
                .reduce((a, b) -> b)
                .map(t -> bpmToSecPerTick(t.bpm))
                .orElse(bpmToSecPerTick(DEFAULT_BPM));

        final ToDoubleFunction<Long> vibratoFunction = tick -> {
            final double currentSec = transformer.tickToSec(tick);

            if (currentSec < vibratoStartSec) return 0.0;

            // Ease-in factor
            final double easeInFactor = Math.min(
                    Math.max((currentSec - vibratoStartSec) / easeInSec, 0.0),
                    1.0
            );

            // Ease-out factor
            final double easeOutFactor = Math.min(
                    Math.max((noteEndSec - currentSec) / easeOutSec, 0.0),
                    1.0
            );

            // Phase calculation
            final double phase = 2 * Math.PI * frequencyHz *
                    (tick - vibratoStartTick) * secPerTick +
                    phaseRad;

            // Envelope lookup
            final double envelope = vibratoEnv.getOrDefault(tick, 1.0);

            return envelope * depthSemitone *
                    easeInFactor * easeOutFactor *
                    Math.sin(phase);
        };
        // endregion

        // region Points Processing
        List<Pair<Long, Double>> basePoints = points.isEmpty() ?
                Arrays.asList(
                        new Pair<>(note.noteStartTick, 0.0),
                        new Pair<>(note.getNoteEndTick(), 0.0)
                ) :
                points;

        if (basePoints.get(basePoints.size() - 1).first != note.getNoteEndTick()) {
            basePoints = new ArrayList<>(basePoints);
            basePoints.add(new Pair<>(
                    note.getNoteEndTick(),
                    basePoints.get(basePoints.size() - 1).second
            ));
        }

        List<Pair<Long, Double>> result = new ArrayList<>();
        Pair<Long, Double> lastPoint = null;

        for (Pair<Long, Double> current : basePoints) {
            if (lastPoint != null) {
                // Generate interpolated points
                long start = lastPoint.first + 1;
                long end = current.first;

                for (long tick = start; tick < end; tick++) {
                    if ((tick - lastPoint.first) % SAMPLING_INTERVAL_TICK == 0) {
                        double value = lastPoint.second + vibratoFunction.applyAsDouble(tick);
                        result.add(new Pair<>(tick, value));
                    }
                }
            }

            // Add original point with vibrato
            double value = current.second + vibratoFunction.applyAsDouble(current.first);
            result.add(new Pair<>(current.first, value));
            lastPoint = current;
        }

        return result;
        // endregion
    }

    private static double resolveParameter(
            Double noteValue,
            Double defaultValue,
            double fallback) {
        return noteValue != null ? noteValue :
                (defaultValue != null ? defaultValue : fallback);
    }
    // endregion

    public static List<Pair<Long, Double>> removeRedundantPoints(
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
    // endregion

    // region Additional classes
    public static class LongRange {
        public final long start;
        public final long end;

        public LongRange(long start, long end) {
            this.start = start;
            this.end = end;
        }

        public boolean contains(long value) {
            return value >= start && value < end;
        }
    }
    // endregion
}