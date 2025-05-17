package tech.konata.convert.pitch;

import tech.konata.convert.Tempo;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static tech.konata.convert.pitch.PitchConverter.DEFAULT_BPM;
import static tech.konata.convert.pitch.PitchConverter.TICKS_IN_BEAT;

public class TickTimeTransformer {
    private final List<Tempo> tempos;

    public TickTimeTransformer(List<Tempo> tempos) {
        // 按 tick 位置排序
        this.tempos = tempos.stream()
            .sorted(Comparator.comparingLong(a -> a.tickPosition))
            .collect(Collectors.toList());
    }

    // region Tick to Time Conversion
    public double tickToSec(long tick) {
        if (tempos.isEmpty()) return tick * bpmToSecPerTick(DEFAULT_BPM);

        double accumulatedSec = 0.0;
        long lastTick = 0L;
        double lastBpm = DEFAULT_BPM;

        for (Tempo tempo : tempos) {
            if (tempo.tickPosition > tick) break;

            long deltaTick = tempo.tickPosition - lastTick;
            accumulatedSec += deltaTick * bpmToSecPerTick(lastBpm);
            lastTick = tempo.tickPosition;
            lastBpm = tempo.bpm;
        }

        long remainingTick = tick - lastTick;
        return accumulatedSec + remainingTick * bpmToSecPerTick(lastBpm);
    }

    public long secToTick(double sec) {
        if (tempos.isEmpty()) return (long) (sec / bpmToSecPerTick(DEFAULT_BPM));

        double remainingSec = sec;
        long accumulatedTick = 0L;
        long lastTick = 0L;
        double lastBpm = DEFAULT_BPM;

        for (Tempo tempo : tempos) {
            double maxSecInSegment = (tempo.tickPosition - lastTick) * bpmToSecPerTick(lastBpm);
            
            if (remainingSec <= maxSecInSegment) {
                return accumulatedTick + (long) (remainingSec / bpmToSecPerTick(lastBpm));
            }

            accumulatedTick += tempo.tickPosition - lastTick;
            remainingSec -= maxSecInSegment;
            lastTick = tempo.tickPosition;
            lastBpm = tempo.bpm;
        }

        return accumulatedTick + (long) (remainingSec / bpmToSecPerTick(lastBpm));
    }
    // endregion

    // region BPM conversion helper
//    private static double bpmToSecPerTick(double bpm) {
//        return 60.0 / (bpm * Resolution.TICKS_PER_BEAT);
//    }
    // endregion

    public static double bpmToSecPerTick(double bpm) {
        return 60.0 / TICKS_IN_BEAT / bpm;
    }
}
