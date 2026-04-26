package tech.konata.convert.pitch;

import tech.konata.convert.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Utility class providing several interpolation strategies over a sequence of
 * {@code (tick, value)} pitch points.
 *
 * <p>Each method takes a sparse list of control points and a sampling-interval
 * (in ticks), and returns a denser list that includes both the original points
 * and the interpolated ones.
 */
public final class InterpolationUtils {

    private InterpolationUtils() { /* static utility class */ }

    /**
     * Linearly interpolates between each pair of consecutive control points.
     */
    public static List<Pair<Long, Double>> interpolateLinear(
            List<Pair<Long, Double>> data,
            long samplingIntervalTick) {
        return interpolate(data, samplingIntervalTick, InterpolationUtils::linearSegment);
    }

    /**
     * Interpolates with a cosine ease-in-out curve (S-curve) between each pair
     * of consecutive control points.
     */
    public static List<Pair<Long, Double>> interpolateCosineEaseInOut(
            List<Pair<Long, Double>> data,
            long samplingIntervalTick) {
        return interpolate(data, samplingIntervalTick, InterpolationUtils::cosineEaseInOutSegment);
    }

    /**
     * Interpolates with a cosine ease-in curve between each pair of consecutive
     * control points.
     */
    public static List<Pair<Long, Double>> interpolateCosineEaseIn(
            List<Pair<Long, Double>> data,
            long samplingIntervalTick) {
        return interpolate(data, samplingIntervalTick, InterpolationUtils::cosineEaseInSegment);
    }

    /**
     * Interpolates with a cosine ease-out curve between each pair of consecutive
     * control points.
     */
    public static List<Pair<Long, Double>> interpolateCosineEaseOut(
            List<Pair<Long, Double>> data,
            long samplingIntervalTick) {
        return interpolate(data, samplingIntervalTick, InterpolationUtils::cosineEaseOutSegment);
    }

    @FunctionalInterface
    private interface SegmentInterpolator {
        List<Pair<Long, Double>> interpolate(
                Pair<Long, Double> start,
                Pair<Long, Double> end,
                List<Long> sampleTicks);
    }

    private static List<Pair<Long, Double>> interpolate(
            List<Pair<Long, Double>> data,
            long samplingIntervalTick,
            SegmentInterpolator interpolator) {

        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }

        List<Pair<Long, Double>> result = new ArrayList<>();

        for (int i = 0; i < data.size() - 1; i++) {
            Pair<Long, Double> start = data.get(i);
            Pair<Long, Double> end   = data.get(i + 1);

            // Collect the ticks that fall strictly between start and end
            // and align to the sampling grid anchored at start.first
            List<Long> sampleTicks = LongStream
                    .range(start.first + 1, end.first)
                    .filter(x -> (x - start.first) % samplingIntervalTick == 0)
                    .boxed()
                    .collect(Collectors.toList());

            result.add(start);
            result.addAll(interpolator.interpolate(start, end, sampleTicks));
        }

        result.add(data.get(data.size() - 1));
        return result;
    }

    private static List<Pair<Long, Double>> linearSegment(
            Pair<Long, Double> start,
            Pair<Long, Double> end,
            List<Long> sampleTicks) {

        long   x0     = start.first;
        double y0     = start.second;
        long   x1     = end.first;
        double y1     = end.second;
        double deltaX = x1 - x0;

        return sampleTicks.stream()
                .map(x -> new Pair<>(x, y0 + (x - x0) * (y1 - y0) / deltaX))
                .collect(Collectors.toList());
    }

    /**
     * Cosine ease-in-out: starts slow, accelerates, then slows again (S-curve).
     */
    private static List<Pair<Long, Double>> cosineEaseInOutSegment(
            Pair<Long, Double> start,
            Pair<Long, Double> end,
            List<Long> sampleTicks) {

        long   x0      = start.first;
        double y0      = start.second;
        long   x1      = end.first;
        double y1      = end.second;
        double yOffset = (y0 + y1) / 2.0;
        double amp     = (y0 - y1) / 2.0;
        double freq    = Math.PI / (x1 - x0);

        return sampleTicks.stream()
                .map(x -> new Pair<>(x, amp * Math.cos(freq * (x - x0)) + yOffset))
                .collect(Collectors.toList());
    }

    /**
     * Cosine ease-in: starts slow, then accelerates toward the end value.
     */
    private static List<Pair<Long, Double>> cosineEaseInSegment(
            Pair<Long, Double> start,
            Pair<Long, Double> end,
            List<Long> sampleTicks) {

        long   x0   = start.first;
        double y0   = start.second;
        long   x1   = end.first;
        double y1   = end.second;
        double amp  = y0 - y1;
        double freq = Math.PI / (x1 - x0) / 2.0;

        return sampleTicks.stream()
                .map(x -> new Pair<>(x, amp * Math.cos(freq * (x - x0)) + y1))
                .collect(Collectors.toList());
    }

    /**
     * Cosine ease-out: starts fast, then decelerates toward the end value.
     */
    private static List<Pair<Long, Double>> cosineEaseOutSegment(
            Pair<Long, Double> start,
            Pair<Long, Double> end,
            List<Long> sampleTicks) {

        long   x0    = start.first;
        double y0    = start.second;
        long   x1    = end.first;
        double y1    = end.second;
        double amp   = y0 - y1;
        double freq  = Math.PI / (x1 - x0) / 2.0;
        double phase = Math.PI / 2.0;

        return sampleTicks.stream()
                .map(x -> new Pair<>(x, amp * Math.cos(freq * (x - x0) + phase) + y0))
                .collect(Collectors.toList());
    }
}
