package tech.konata.convert.pitch;

import tech.konata.convert.Pair;

import java.util.List;

/**
 * Post-processing step applied to relative pitch data before it is written into
 * a Synthesizer V {@code .svp} project file.
 *
 * <p>The SVP pitch-delta channel uses cosine interpolation between points, so we
 * must insert "step" points just before transitions to prevent unintended glides.
 * Consecutive duplicate points are then removed to keep the output compact.
 */
public final class SynthVPitchConversion {

    /** Sampling interval used when preparing pitch data for SVP output. */
    private static final long SAMPLING_INTERVAL_TICK = 4L;

    private SynthVPitchConversion() { /* static utility class */ }

    /**
     * Prepares relative pitch data for Synthesizer V output.
     *
     * <ol>
     *   <li>Calls {@link PitchConverter#appendPitchPointsForInterpolation} to insert
     *       "step" hold-points before transitions, preventing cosine glides.</li>
     *   <li>Calls {@link PitchConverter#reduceRepeatedPitchPoints} to collapse runs
     *       of identical values, keeping the file compact.</li>
     * </ol>
     *
     * @param relativeData relative pitch points ({@code (tick, semitoneOffset)})
     * @return processed list ready for insertion into the {@code pitchDelta} channel
     */
    public static List<Pair<Long, Double>> prepareForSvpOutput(
            List<Pair<Long, Double>> relativeData) {

        List<Pair<Long, Double>> withStepPoints =
                PitchConverter.appendPitchPointsForInterpolation(relativeData, SAMPLING_INTERVAL_TICK);

        return PitchConverter.reduceRepeatedPitchPoints(withStepPoints);
    }
}
