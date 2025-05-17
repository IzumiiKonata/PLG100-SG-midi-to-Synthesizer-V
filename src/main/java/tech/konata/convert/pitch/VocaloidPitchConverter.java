package tech.konata.convert.pitch;

import tech.konata.convert.Note;
import tech.konata.convert.Pair;
import tech.konata.convert.Pitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VocaloidPitchConverter {
    private static final int PITCH_MAX_VALUE = 8191;
    private static final int DEFAULT_PITCH_BEND_SENSITIVITY = 2;
    private static final long MIN_BREAK_LENGTH_BETWEEN_PITCH_SECTIONS = 480L;
    private static final long BORDER_APPEND_RADIUS = 5L;
    private static final String PITCH_BEND_SENSITIVITY_NAME = "PBS";
    private static final String PITCH_BEND_NAME = "PIT";

    public static class VocaloidPartPitchData {
        private final long startPos;
        private final List<Event> pit;
        private final List<Event> pbs;

        public VocaloidPartPitchData(long startPos, List<Event> pit, List<Event> pbs) {
            this.startPos = startPos;
            this.pit = pit;
            this.pbs = pbs;
        }

        public long getStartPos() {
            return startPos;
        }

        public List<Event> getPit() {
            return pit;
        }

        public List<Event> getPbs() {
            return pbs;
        }

        public static class Event {
            private final long pos;
            private final int value;

            public Event(long pos, int value) {
                this.pos = pos;
                this.value = value;
            }

            public static Event fromPair(long pos, int value) {
                return new Event(pos, value);
            }

            public long getPos() {
                return pos;
            }

            public int getValue() {
                return value;
            }
        }
    }

    public static VocaloidPartPitchData generateForVocaloid(Pitch pitch, List<Note> notes) {
        List<Pair<Long, Double>> data = PitchConverter.getRelativeData(pitch, notes, BORDER_APPEND_RADIUS);
        if (data == null || data.isEmpty()) return null;

        List<List<Pair<Long, Double>>> pitchSectioned = new ArrayList<>();
        long currentPos = 0;

        for (Pair<Long, Double> pitchEvent : data) {
            if (pitchSectioned.isEmpty()) {
                pitchSectioned.add(new ArrayList<>(Collections.singletonList(pitchEvent)));
            } else {
                long diff = pitchEvent.getFirst() - currentPos;
                if (diff >= MIN_BREAK_LENGTH_BETWEEN_PITCH_SECTIONS) {
                    pitchSectioned.add(new ArrayList<>(Collections.singletonList(pitchEvent)));
                } else {
                    pitchSectioned.get(pitchSectioned.size() - 1).add(pitchEvent);
                }
            }
            currentPos = pitchEvent.getFirst();
        }

        List<VocaloidPartPitchData.Event> pit = new ArrayList<>();
        List<VocaloidPartPitchData.Event> pbs = new ArrayList<>();

        for (List<Pair<Long, Double>> section : pitchSectioned) {
            double maxAbsValue = 0.0;
            for (Pair<Long, Double> event : section) {
                double absValue = Math.abs(event.getSecond());
                if (absValue > maxAbsValue) {
                    maxAbsValue = absValue;
                }
            }

            int pbsForThisSection = (int) Math.ceil(maxAbsValue);
            if (pbsForThisSection > DEFAULT_PITCH_BEND_SENSITIVITY) {
                long firstPos = section.get(0).getFirst();
                pbs.add(new VocaloidPartPitchData.Event(firstPos, pbsForThisSection));
                long lastPosPlus = section.get(section.size() - 1).getFirst() +
                    MIN_BREAK_LENGTH_BETWEEN_PITCH_SECTIONS / 2;
                pbs.add(new VocaloidPartPitchData.Event(lastPosPlus, DEFAULT_PITCH_BEND_SENSITIVITY));
            } else {
                pbsForThisSection = DEFAULT_PITCH_BEND_SENSITIVITY;
            }

            for (Pair<Long, Double> event : section) {
                int value = (int) Math.round(event.getSecond() * PITCH_MAX_VALUE / pbsForThisSection);
                value = Math.max(-PITCH_MAX_VALUE, Math.min(PITCH_MAX_VALUE, value));
                pit.add(new VocaloidPartPitchData.Event(event.getFirst(), value));
            }
        }

        return new VocaloidPartPitchData(0, pit, pbs);
    }

}