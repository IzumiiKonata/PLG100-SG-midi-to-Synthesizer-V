package tech.konata.convert.pitch;

import tech.konata.convert.Pair;

import java.util.*;
import java.util.stream.*;

public class InterpolationUtils {
    
    // region 公共插值方法
    public static List<Pair<Long, Double>> interpolateLinear(
            List<Pair<Long, Double>> data, 
            long samplingIntervalTick) {
        return interpolate(data, samplingIntervalTick, InterpolationUtils::linearMapping);
    }

    public static List<Pair<Long, Double>> interpolateCosineEaseInOut(
            List<Pair<Long, Double>> data,
            long samplingIntervalTick) {
        return interpolate(data, samplingIntervalTick, InterpolationUtils::cosineEaseInOutMapping);
    }

    public static List<Pair<Long, Double>> interpolateCosineEaseIn(
            List<Pair<Long, Double>> data,
            long samplingIntervalTick) {
        return interpolate(data, samplingIntervalTick, InterpolationUtils::cosineEaseInMapping);
    }

    public static List<Pair<Long, Double>> interpolateCosineEaseOut(
            List<Pair<Long, Double>> data,
            long samplingIntervalTick) {
        return interpolate(data, samplingIntervalTick, InterpolationUtils::cosineEaseOutMapping);
    }
    // endregion

    // region 核心插值逻辑
    @FunctionalInterface
    private interface InterpolationFunction {
        List<Pair<Long, Double>> apply(
            Pair<Long, Double> start, 
            Pair<Long, Double> end, 
            List<Long> indexes
        );
    }

    private static List<Pair<Long, Double>> interpolate(
            List<Pair<Long, Double>> data,
            long samplingIntervalTick,
            InterpolationFunction mapping) {
        if (data == null || data.isEmpty()) return Collections.emptyList();

        List<Pair<Long, Double>> result = new ArrayList<>();
        for (int i = 0; i < data.size() - 1; i++) {
            Pair<Long, Double> start = data.get(i);
            Pair<Long, Double> end = data.get(i + 1);
            
            // 生成需要插值的索引
            List<Long> indexes = LongStream.range(start.first + 1, end.first)
                .filter(x -> (x - start.first) % samplingIntervalTick == 0)
                .boxed()
                .collect(Collectors.toList());

            // 执行映射函数
            List<Pair<Long, Double>> interpolated = mapping.apply(start, end, indexes);
            
            result.add(start);
            result.addAll(interpolated);
        }
        result.add(data.get(data.size() - 1));
        return result;
    }
    // endregion

    // region 具体插值算法实现
    private static List<Pair<Long, Double>> linearMapping(
            Pair<Long, Double> start, 
            Pair<Long, Double> end, 
            List<Long> indexes) {
        long x0 = start.first;
        double y0 = start.second;
        long x1 = end.first;
        double y1 = end.second;
        double deltaX = x1 - x0;

        return indexes.stream()
            .map(x -> {
                double y = y0 + (x - x0) * (y1 - y0) / deltaX;
                return new Pair<>(x, y);
            })
            .collect(Collectors.toList());
    }

    private static List<Pair<Long, Double>> cosineEaseInOutMapping(
            Pair<Long, Double> start, 
            Pair<Long, Double> end, 
            List<Long> indexes) {
        long x0 = start.first;
        double y0 = start.second;
        long x1 = end.first;
        double y1 = end.second;
        double yOffset = (y0 + y1) / 2;
        double amp = (y0 - y1) / 2;
        double aFreq = Math.PI / (x1 - x0);

        return indexes.stream()
            .map(x -> {
                double y = amp * Math.cos(aFreq * (x - x0)) + yOffset;
                return new Pair<>(x, y);
            })
            .collect(Collectors.toList());
    }

    private static List<Pair<Long, Double>> cosineEaseInMapping(
            Pair<Long, Double> start, 
            Pair<Long, Double> end, 
            List<Long> indexes) {
        long x0 = start.first;
        double y0 = start.second;
        long x1 = end.first;
        double y1 = end.second;
        double yOffset = y1;
        double amp = y0 - y1;
        double aFreq = Math.PI / (x1 - x0) / 2;

        return indexes.stream()
            .map(x -> {
                double y = amp * Math.cos(aFreq * (x - x0)) + yOffset;
                return new Pair<>(x, y);
            })
            .collect(Collectors.toList());
    }

    private static List<Pair<Long, Double>> cosineEaseOutMapping(
            Pair<Long, Double> start, 
            Pair<Long, Double> end, 
            List<Long> indexes) {
        long x0 = start.first;
        double y0 = start.second;
        long x1 = end.first;
        double y1 = end.second;
        double yOffset = y0;
        double amp = y0 - y1;
        double aFreq = Math.PI / (x1 - x0) / 2;
        double phase = Math.PI / 2;

        return indexes.stream()
            .map(x -> {
                double y = amp * Math.cos(aFreq * (x - x0) + phase) + yOffset;
                return new Pair<>(x, y);
            })
            .collect(Collectors.toList());
    }
    // endregion

}