package com.bike.monitor.flink.clean.filter;

import com.bike.monitor.common.dto.GpsPoint;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 5 点滑动中值滤波（题 1 第三层）：对最近 5 个（含当前）经/纬度分别取中值，
 * 消除短时随机毛刺。经、纬通道独立取中值。
 */
public class MedianFilter extends KeyedProcessFunction<String, GpsPoint, GpsPoint> {

    private static final int WINDOW = 5;

    private transient ListState<Double> lngBuffer;
    private transient ListState<Double> latBuffer;

    @Override
    public void open(Configuration parameters) {
        lngBuffer = getRuntimeContext().getListState(new ListStateDescriptor<>("medianLngBuf", Double.class));
        latBuffer = getRuntimeContext().getListState(new ListStateDescriptor<>("medianLatBuf", Double.class));
    }

    @Override
    public void processElement(GpsPoint point, Context ctx, Collector<GpsPoint> out) throws Exception {
        List<Double> lngs = pruneToWindow(lngBuffer, point.getLng());
        List<Double> lats = pruneToWindow(latBuffer, point.getLat());

        out.collect(GpsPoint.builder()
                .bikeId(point.getBikeId())
                .district(point.getDistrict())
                .lng(median(lngs))
                .lat(median(lats))
                .speed(point.getSpeed())
                .direction(point.getDirection())
                .locked(point.isLocked())
                .ts(point.getTs())
                .build());
    }

    /** 追加当前值并裁剪到最近 WINDOW 个，写回 state */
    private List<Double> pruneToWindow(ListState<Double> state, double value) throws Exception {
        List<Double> list = new ArrayList<>();
        if (state.get() != null) {
            state.get().forEach(list::add);
        }
        list.add(value);
        while (list.size() > WINDOW) {
            list.remove(0);
        }
        state.update(list);
        return list;
    }

    private double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }
}
