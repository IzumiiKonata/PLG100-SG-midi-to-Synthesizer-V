package tech.konata.convert.pitch;

import tech.konata.convert.Pair;

import java.util.List;

/**
 * @author IzumiiKonata
 * Date: 2025/5/17 10:35
 */
public class SynthVPitchConvertion {

    private static final long SAMPLING_INTERVAL_TICK = 4L;

    public static List<Pair<Long, Double>> appendPitchPointsForSvpOutput(List<Pair<Long, Double>> data) {
        return PitchConverter.reduceRepeatedPitchPoints(PitchConverter.appendPitchPointsForInterpolation(data, SAMPLING_INTERVAL_TICK));
    }


}
