package tech.konata.convert.pitch;

import tech.konata.convert.Tempo;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static tech.konata.convert.pitch.PitchConverter.DEFAULT_BPM;
import static tech.konata.convert.pitch.PitchConverter.TICKS_IN_BEAT;

/**
 * Converts between tick positions and wall-clock time in seconds, taking into
 * account a (possibly changing) tempo map.
 *
 * <p>The tempo list supplied at construction time is sorted by tick position and
 * treated as immutable. Each entry defines the BPM that is in effect from its
 * tick position onward.
 */
public final class TickTimeTransformer {

    private final List<Tempo> sortedTempos;

    public TickTimeTransformer(List<Tempo> tempos) {
        this.sortedTempos = tempos.stream()
                .sorted(Comparator.comparingLong(t -> t.tickPosition))
                .collect(Collectors.toList());
    }

    /**
     * Converts a tick position to seconds, honouring all tempo changes.
     *
     * @param tick absolute tick position (≥ 0)
     * @return elapsed time in seconds from tick 0
     */
    public double tickToSec(long tick) {
        if (sortedTempos.isEmpty()) {
            return tick * bpmToSecPerTick(DEFAULT_BPM);
        }

        double accumulatedSec = 0.0;
        long   lastTick       = 0L;
        double lastBpm        = DEFAULT_BPM;

        for (Tempo tempo : sortedTempos) {
            if (tempo.tickPosition >= tick) break;

            accumulatedSec += (tempo.tickPosition - lastTick) * bpmToSecPerTick(lastBpm);
            lastTick = tempo.tickPosition;
            lastBpm  = tempo.bpm;
        }

        return accumulatedSec + (tick - lastTick) * bpmToSecPerTick(lastBpm);
    }

    /**
     * Converts a time in seconds to the closest tick position, honouring all
     * tempo changes.
     *
     * @param sec elapsed time in seconds from the start of the song
     * @return corresponding tick position
     */
    public long secToTick(double sec) {
        if (sortedTempos.isEmpty()) {
            return (long) (sec / bpmToSecPerTick(DEFAULT_BPM));
        }

        double remainingSec      = sec;
        long   accumulatedTick   = 0L;
        long   lastTick          = 0L;
        double lastBpm           = DEFAULT_BPM;

        for (Tempo tempo : sortedTempos) {
            double segmentDurationSec = (tempo.tickPosition - lastTick) * bpmToSecPerTick(lastBpm);

            if (remainingSec <= segmentDurationSec) {
                return accumulatedTick + (long) (remainingSec / bpmToSecPerTick(lastBpm));
            }

            accumulatedTick += tempo.tickPosition - lastTick;
            remainingSec    -= segmentDurationSec;
            lastTick         = tempo.tickPosition;
            lastBpm          = tempo.bpm;
        }

        return accumulatedTick + (long) (remainingSec / bpmToSecPerTick(lastBpm));
    }

    /**
     * Returns the duration of one tick in seconds at the given BPM.
     *
     * @param bpm beats per minute
     * @return seconds per tick
     */
    public static double bpmToSecPerTick(double bpm) {
        return 60.0 / TICKS_IN_BEAT / bpm;
    }
}
