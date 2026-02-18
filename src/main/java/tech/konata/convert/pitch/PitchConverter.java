package tech.konata.convert.pitch;

import tech.konata.convert.Note;
import tech.konata.convert.Pair;
import tech.konata.convert.Pitch;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class PitchConverter {
    
    public static final int TICKS_IN_BEAT = 480;
    public static final int TICKS_IN_FULL_NOTE = TICKS_IN_BEAT * 4;
    public static final int KEY_IN_OCTAVE = 12;
    public static final String DEFAULT_LYRIC = "あ";
    public static final double DEFAULT_BPM = 120.0;
    public static final int DEFAULT_METER_HIGH = 4;
    public static final int DEFAULT_METER_LOW = 4;
    public static final int DEFAULT_KEY = 60;
    public static final double KEY_CENTER_C = 60.0;
    public static final double LOG_FRQ_CENTER_C = 5.566914341;
    public static final double LOG_FRQ_DIFF_ONE_KEY = 0.05776226505;
    
    // region Extension functions conversions
    public static double loggedFrequencyToKey(double $this) {
        return KEY_CENTER_C + ($this - LOG_FRQ_CENTER_C) / LOG_FRQ_DIFF_ONE_KEY;
    }

    public static double keyToLoggedFrequency(double $this) {
        return ($this - KEY_CENTER_C) * LOG_FRQ_DIFF_ONE_KEY + LOG_FRQ_CENTER_C;
    }
    // endregion

    // region Main conversion logic
    public static List<Pair<Long, Double>> getAbsoluteData(Pitch pitch, List<Note> notes) {
        return convertRelativity(pitch, notes, true, 0L);
    }

    public static List<Pair<Long, Double>> getRelativeData(Pitch pitch, List<Note> notes) {
        return getRelativeData(pitch, notes, 0L);
    }

    public static List<Pair<Long, Double>> getRelativeData(Pitch pitch, List<Note> notes, long borderAppendRadius) {
        List<Pair<Long, Double>> result = convertRelativity(pitch, notes, false, borderAppendRadius);
        return result != null ? 
            result.stream()
                .filter(p -> p.second != null)
                .map(p -> new Pair<>(p.first, p.second))
                .collect(Collectors.toList()) : 
            null;
    }

    private static List<Pair<Long, Double>> convertRelativity(
        Pitch pitch,
        List<Note> notes,
        boolean toAbsolute,
        long borderAppendRadius
    ) {
        if (pitch.isAbsolute() == toAbsolute) {
            return pitch.getData().stream()
                .map(p -> new Pair<>(p.first, p.second))
                .collect(Collectors.toList());
        }
        if (notes.isEmpty()) return null;

        List<Long> borders = getBorders(notes);
        int[] indexWrapper = {0};
        double[] currentNoteKey = {notes.get(0).getKey()};
        long[] nextBorder = {borders.isEmpty() ? Long.MAX_VALUE : borders.get(0)};

        Stream<Pair<Long, Double>> processedStream = pitch.getData().stream()
            .map(p -> {
                while (p.first >= nextBorder[0]) {
                    indexWrapper[0]++;
                    nextBorder[0] = indexWrapper[0] < borders.size() ? 
                        borders.get(indexWrapper[0]) : 
                        Long.MAX_VALUE;
                    currentNoteKey[0] = notes.get(indexWrapper[0]).getKey();
                }
                
                Double convertedValue = null;
                if (p.second != null) {
                    if (pitch.isAbsolute()) {
                        convertedValue = p.second - currentNoteKey[0];
                    } else {
                        convertedValue = (p.second == 0.0) ? null : p.second + currentNoteKey[0];
                    }
                }
                return new Pair<>(p.first, convertedValue);
            });

        if (!toAbsolute) {
            List<Pair<Long, Double>> temp = processedStream.collect(Collectors.toList());
            return appendPointsAtBorders(temp, notes, borderAppendRadius);
        }
        return processedStream.collect(Collectors.toList());
    }
    // endregion

    // region Helper methods
    private static List<Long> getBorders(List<Note> notes) {
        List<Long> borders = new ArrayList<>();
        long pos = -1L;
        for (Note note : notes) {
            if (pos < 0) {
                pos = note.getTickOff();
                continue;
            }
            
            if (pos == note.getTickOn()) {
                borders.add(pos);
            } else if (pos < note.getTickOn()) {
                borders.add((note.getTickOn() + pos) / 2);
            } else {
                throw new RuntimeException("NotesOverlapping");
            }
            pos = note.getTickOff();
        }
        return borders;
    }

    private static List<Pair<Long, Double>> appendPointsAtBorders(
        List<Pair<Long, Double>> data,
        List<Note> notes,
        long radius
    ) {
        if (radius <= 0) return data;
        
        List<Pair<Long, Double>> result = new ArrayList<>(data);
        for (int i = 0; i < notes.size() - 1; i++) {
            Note lastNote = notes.get(i);
            Note thisNote = notes.get(i + 1);
            
            if (thisNote.getTickOn() - lastNote.getTickOff() <= radius) {
                int finalI = i;
                int firstPointIndex = IntStream.range(0, result.size())
                    .filter(idx -> result.get(idx).first >= thisNote.getTickOn())
                    .findFirst()
                    .orElse(-1);
                
                if (firstPointIndex >= 0) {
                    Pair<Long, Double> firstPoint = result.get(firstPointIndex);
                    if (firstPoint.first != thisNote.getTickOn() &&
                        firstPoint.first - thisNote.getTickOn() <= radius) {
                        Long newPointTick = thisNote.getTickOn() - radius;
                        Pair<Long, Double> newPoint = 
                            new Pair<>(newPointTick, firstPoint.second);
                        
                        // Remove points in range
                        result.removeIf(p -> 
                            p.first >= newPointTick && p.first < thisNote.getTickOn() &&
                            !p.equals(newPoint));
                        result.add(firstPointIndex, newPoint);
                    }
                }
            }
        }
        return result;
    }

    public static List<Pair<Long, Double>> appendPitchPointsForInterpolation(
        List<Pair<Long, Double>> points,
        long intervalTick
    ) {
        if (points.isEmpty()) return Collections.emptyList();
        
        List<Pair<Long, Double>> result = new ArrayList<>();
        result.add(points.get(0));
        
        for (int i = 0; i < points.size() - 1; i++) {
            Pair<Long, Double> last = points.get(i);
            Pair<Long, Double> current = points.get(i + 1);
            
            long tickDiff = current.first - last.first;
            if (tickDiff >= intervalTick) {
                Pair<Long, Double> newPoint;
                if (tickDiff < 2 * intervalTick) {
                    newPoint = new Pair<>((current.first + last.first) / 2, last.second);
                } else {
                    newPoint = new Pair<>(current.first - intervalTick, last.second);
                }
                result.add(newPoint);
            }
            result.add(current);
        }
        return result;
    }

    public static List<Pair<Long, Double>> reduceRepeatedPitchPoints(
        List<Pair<Long, Double>> points
    ) {
        Set<Pair<Long, Double>> toRemove = new HashSet<>();
        Double currentValue = null;
        Pair<Long, Double> prev = null;
        
        for (Pair<Long, Double> p : points) {
            if (prev == null) {
                prev = p;
                continue;
            }
            
            if (currentValue == null) {
                if (Objects.equals(prev.second, p.second)) {
                    currentValue = p.second;
                }
            } else {
                if (Objects.equals(currentValue, p.second)) {
                    toRemove.add(prev);
                } else {
                    currentValue = null;
                }
            }
            prev = p;
        }
        return points.stream()
            .filter(p -> !toRemove.contains(p))
            .collect(Collectors.toList());
    }

    // endregion

    public static List<Pair<Long, Double>> reduceRepeatedPoints(List<Pair<Long, Double>> list) {
        Set<Pair<Long, Double>> toBeRemoved = new HashSet<>();
        Double currentRepeatedValue = null;
        Pair<Long, Double> prevPoint = null;

        for (Pair<Long, Double> point : list) {
            if (prevPoint == null) {
                prevPoint = point;
                continue;
            }

            if (currentRepeatedValue == null) {
                if (Objects.equals(prevPoint.second, point.second)) {
                    currentRepeatedValue = point.second;
                }
                prevPoint = point;
                continue;
            }

            if (Objects.equals(currentRepeatedValue, point.second)) {
                toBeRemoved.add(prevPoint);
            } else {
                currentRepeatedValue = null;
            }

            prevPoint = point;
        }

        return list.stream()
                .filter(p -> !toBeRemoved.contains(p))
                .collect(Collectors.toList());
    }

}