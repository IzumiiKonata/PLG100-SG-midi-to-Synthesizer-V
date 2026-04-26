package tech.konata.convert.pitch;

import tech.konata.convert.Note;
import tech.konata.convert.Pair;
import tech.konata.convert.Pitch;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Core pitch-data utilities: conversion between absolute and relative pitch
 * representations, and helpers for preparing pitch data for interpolation and
 * output.
 *
 * <h2>Terminology</h2>
 * <ul>
 *   <li><b>Absolute pitch</b> – values are MIDI key numbers (e.g. 60 = middle C).</li>
 *   <li><b>Relative pitch</b> – values are semitone offsets from the current note's
 *       key (0.0 means "in tune with the note").</li>
 * </ul>
 */
public final class PitchConverter {

    public static final int    TICKS_IN_BEAT      = 480;
    public static final int    TICKS_IN_FULL_NOTE  = TICKS_IN_BEAT * 4;
    public static final int    KEYS_IN_OCTAVE      = 12;
    public static final String DEFAULT_LYRIC       = "あ";
    public static final double DEFAULT_BPM         = 120.0;
    public static final int    DEFAULT_METER_HIGH  = 4;
    public static final int    DEFAULT_METER_LOW   = 4;
    public static final int    DEFAULT_KEY         = 60;

    /** MIDI key number of middle C (C4). */
    public static final double KEY_CENTER_C = 60.0;

    /** Log-frequency value corresponding to middle C. */
    public static final double LOG_FREQ_CENTER_C = 5.566914341;

    /** Log-frequency difference per semitone. */
    public static final double LOG_FREQ_DIFF_PER_KEY = 0.05776226505;

    private PitchConverter() { /* static utility class */ }

    /**
     * Converts a logged frequency value to a MIDI key number.
     */
    public static double loggedFrequencyToKey(double logFreq) {
        return KEY_CENTER_C + (logFreq - LOG_FREQ_CENTER_C) / LOG_FREQ_DIFF_PER_KEY;
    }

    /**
     * Converts a MIDI key number to a logged frequency value.
     */
    public static double keyToLoggedFrequency(double key) {
        return (key - KEY_CENTER_C) * LOG_FREQ_DIFF_PER_KEY + LOG_FREQ_CENTER_C;
    }

    /**
     * Returns the pitch data as absolute (MIDI-key) values.
     * If the source is already absolute, the data is returned as-is.
     *
     * @param pitch source pitch data
     * @param notes note list that defines the key at each tick
     * @return list of {@code (tick, absoluteKey)} pairs, or {@code null} if the
     *         notes list is empty and conversion is needed
     */
    public static List<Pair<Long, Double>> getAbsoluteData(Pitch pitch, List<Note> notes) {
        return convertPitchRelativity(pitch, notes, /* toAbsolute */ true, /* borderRadius */ 0L);
    }

    /**
     * Returns the pitch data as relative (semitone-offset) values, without any
     * border-append smoothing.
     *
     * @see #getRelativeData(Pitch, List, long)
     */
    public static List<Pair<Long, Double>> getRelativeData(Pitch pitch, List<Note> notes) {
        return getRelativeData(pitch, notes, 0L);
    }

    /**
     * Returns the pitch data as relative (semitone-offset) values.
     *
     * <p>Points with a {@code null} value (representing "no offset" at a note
     * boundary) are filtered out. Optionally, extra anchor points are appended
     * near note transitions within {@code borderAppendRadius} ticks, which helps
     * some synthesisers avoid unwanted glides between notes.
     *
     * @param pitch              source pitch data
     * @param notes              note list that defines the key at each tick
     * @param borderAppendRadius ticks within which to append transition anchor
     *                           points (0 = disabled)
     * @return list of {@code (tick, relativeOffset)} pairs, or {@code null}
     */
    public static List<Pair<Long, Double>> getRelativeData(
            Pitch pitch, List<Note> notes, long borderAppendRadius) {

        List<Pair<Long, Double>> result =
                convertPitchRelativity(pitch, notes, /* toAbsolute */ false, borderAppendRadius);

        if (result == null) return null;

        return result.stream()
                .filter(p -> p.second != null)
                .collect(Collectors.toList());
    }

    /**
     * Converts pitch data between absolute and relative representations.
     *
     * @param pitch              source pitch
     * @param notes              note list (must not be empty when conversion is needed)
     * @param toAbsolute         {@code true} → convert to absolute; {@code false} → convert to relative
     * @param borderAppendRadius radius for border-append pass (relative only)
     */
    private static List<Pair<Long, Double>> convertPitchRelativity(
            Pitch pitch,
            List<Note> notes,
            boolean toAbsolute,
            long borderAppendRadius) {

        // If the data is already in the target form, return a copy directly
        if (pitch.isAbsolute() == toAbsolute) {
            return new ArrayList<>(pitch.getData());
        }

        if (notes.isEmpty()) return null;

        // Pre-compute the tick at which each note transition ("border") falls
        List<Long> borders = computeNoteBorders(notes);

        // Mutable state shared across the stream
        int[]    noteIndexRef   = { 0 };
        double[] currentKeyRef  = { notes.get(0).getKey() };
        long[]   nextBorderRef  = { borders.isEmpty() ? Long.MAX_VALUE : borders.get(0) };

        Stream<Pair<Long, Double>> converted = pitch.getData().stream()
                .map(point -> {
                    // Advance note index when we pass a border tick
                    while (point.first >= nextBorderRef[0]) {
                        noteIndexRef[0]++;
                        nextBorderRef[0] = noteIndexRef[0] < borders.size()
                                ? borders.get(noteIndexRef[0])
                                : Long.MAX_VALUE;
                        currentKeyRef[0] = notes.get(noteIndexRef[0]).getKey();
                    }

                    Double convertedValue = null;
                    if (point.second != null) {
                        if (pitch.isAbsolute()) {
                            // absolute → relative
                            convertedValue = point.second - currentKeyRef[0];
                        } else {
                            // relative → absolute (0.0 offset means "no data" → null)
                            convertedValue = (point.second == 0.0)
                                    ? null
                                    : point.second + currentKeyRef[0];
                        }
                    }
                    return new Pair<>(point.first, convertedValue);
                });

        if (!toAbsolute) {
            List<Pair<Long, Double>> temp = converted.collect(Collectors.toList());
            return appendPointsAtNoteBorders(temp, notes, borderAppendRadius);
        }

        return converted.collect(Collectors.toList());
    }

    /**
     * Computes the tick at which each adjacent note pair transitions.
     *
     * <ul>
     *   <li>If notes are back-to-back (no gap), the border is at the shared tick.</li>
     *   <li>If there is a gap, the border is placed at the midpoint of the gap.</li>
     *   <li>Overlapping notes are not supported and will throw.</li>
     * </ul>
     */
    private static List<Long> computeNoteBorders(List<Note> notes) {
        List<Long> borders = new ArrayList<>();
        long prevTickOff = -1L;

        for (Note note : notes) {
            if (prevTickOff < 0) {
                prevTickOff = note.getTickOff();
                continue;
            }

            long nextTickOn = note.getTickOn();
            if (prevTickOff == nextTickOn) {
                borders.add(prevTickOff);
            } else if (prevTickOff < nextTickOn) {
                borders.add((prevTickOff + nextTickOn) / 2L);
            } else {
                throw new IllegalStateException("Notes overlap: tickOff=" + prevTickOff
                        + " > nextTickOn=" + nextTickOn);
            }
            prevTickOff = note.getTickOff();
        }
        return borders;
    }

    /**
     * Appends transition anchor points near note-on ticks to prevent synthesisers
     * from applying pitch glides across note boundaries.
     *
     * <p>For each adjacent note pair where the gap is ≤ {@code radius}, if the
     * first pitch point that falls on or after the note-on is within {@code radius}
     * ticks, a copy of that point is placed exactly {@code radius} ticks before
     * the note-on.
     */
    private static List<Pair<Long, Double>> appendPointsAtNoteBorders(
            List<Pair<Long, Double>> data,
            List<Note> notes,
            long radius) {

        if (radius <= 0) return data;

        List<Pair<Long, Double>> result = new ArrayList<>(data);

        for (int i = 0; i < notes.size() - 1; i++) {
            Note lastNote = notes.get(i);
            Note thisNote = notes.get(i + 1);

            if (thisNote.getTickOn() - lastNote.getTickOff() > radius) continue;

            // Find the first point at or after this note's start
            OptionalInt firstIndexOpt = IntStream.range(0, result.size())
                    .filter(idx -> result.get(idx).first >= thisNote.getTickOn())
                    .findFirst();

            if (firstIndexOpt.isEmpty()) continue;

            int               firstIndex = firstIndexOpt.getAsInt();
            Pair<Long, Double> firstPoint = result.get(firstIndex);

            if (firstPoint.first == thisNote.getTickOn()) continue;
            if (firstPoint.first - thisNote.getTickOn() > radius) continue;

            long newTick = thisNote.getTickOn() - radius;
            Pair<Long, Double> anchor = new Pair<>(newTick, firstPoint.second);

            // Remove any existing points in [newTick, noteOn) that aren't the new anchor
            result.removeIf(p -> p.first >= newTick
                    && p.first < thisNote.getTickOn()
                    && !p.equals(anchor));

            // Insert the anchor before firstIndex (index may have shifted)
            int insertAt = IntStream.range(0, result.size())
                    .filter(idx -> result.get(idx).first >= thisNote.getTickOn())
                    .findFirst()
                    .orElse(result.size());
            result.add(insertAt, anchor);
        }

        return result;
    }

    /**
     * Inserts intermediate "step" points between consecutive pitch points that are
     * far apart, so that subsequent cosine-interpolation produces smooth curves
     * rather than sudden jumps.
     *
     * <p>For each pair of adjacent points, if their tick distance is ≥
     * {@code intervalTick}, a point is added at the previous value level just
     * before the second point:
     * <ul>
     *   <li>If gap &lt; 2 × interval: the step is placed at the midpoint.</li>
     *   <li>Otherwise: the step is placed exactly {@code intervalTick} before the
     *       second point.</li>
     * </ul>
     */
    public static List<Pair<Long, Double>> appendPitchPointsForInterpolation(
            List<Pair<Long, Double>> points,
            long intervalTick) {

        if (points.isEmpty()) return Collections.emptyList();

        List<Pair<Long, Double>> result = new ArrayList<>();
        result.add(points.get(0));

        for (int i = 0; i < points.size() - 1; i++) {
            Pair<Long, Double> prev    = points.get(i);
            Pair<Long, Double> current = points.get(i + 1);
            long tickGap = current.first - prev.first;

            if (tickGap >= intervalTick) {
                long stepTick = (tickGap < 2 * intervalTick)
                        ? (current.first + prev.first) / 2L
                        : current.first - intervalTick;
                result.add(new Pair<>(stepTick, prev.second));
            }
            result.add(current);
        }
        return result;
    }

    /**
     * Removes redundant intermediate points that have the same value as both their
     * predecessor and their successor, while preserving the first and last point of
     * any run of identical values.
     *
     * <p>This is equivalent to {@link #reduceRepeatedPoints(List)} and is kept for
     * API compatibility.
     */
    public static List<Pair<Long, Double>> reduceRepeatedPitchPoints(
            List<Pair<Long, Double>> points) {
        return reduceRepeatedPoints(points);
    }

    /**
     * Removes every point that is sandwiched between two points with the same
     * value — i.e. it removes interior points in any run of identical values,
     * keeping only the first and last of each run.
     */
    public static List<Pair<Long, Double>> reduceRepeatedPoints(
            List<Pair<Long, Double>> points) {

        Set<Pair<Long, Double>> toRemove = new HashSet<>();
        Double currentRunValue = null;
        Pair<Long, Double> prev = null;

        for (Pair<Long, Double> point : points) {
            if (prev == null) {
                prev = point;
                continue;
            }

            if (currentRunValue == null) {
                if (Objects.equals(prev.second, point.second)) {
                    currentRunValue = point.second;
                }
            } else {
                if (Objects.equals(currentRunValue, point.second)) {
                    toRemove.add(prev);
                } else {
                    currentRunValue = null;
                }
            }
            prev = point;
        }

        return points.stream()
                .filter(p -> !toRemove.contains(p))
                .collect(Collectors.toList());
    }
}
