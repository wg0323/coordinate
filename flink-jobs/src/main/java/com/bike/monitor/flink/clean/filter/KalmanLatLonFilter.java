package com.bike.monitor.flink.clean.filter;

import com.bike.monitor.common.dto.GpsPoint;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

/**
 * 经/纬双通道标量卡尔曼滤波（题 1 第二层）。
 *
 * <p>模型：静态模型（位置随机游走，x' = x，P' = P + Q），观测为当前上报坐标。
 * 经度、纬度作为两个独立一维通道分别滤波，忽略通道间相关性——
 * 这是工程上的常见简化（题面允许自实现一维 KF），完整方案为二维 EKF。</p>
 */
public class KalmanLatLonFilter extends KeyedProcessFunction<String, GpsPoint, GpsPoint> {

    /** 过程噪声（度²/步），对应车辆真实机动的不可预测分量 */
    static final double PROCESS_NOISE = 1e-9;
    /** 观测噪声（度²），对应 GPS 水平定位误差（约 10m ≈ 9e-5 度 → 方差 ~8e-9） */
    static final double MEASUREMENT_NOISE = 8e-9;

    /** 状态 [x, P]：估计值与估计方差 */
    private transient ValueState<double[]> lngState;
    private transient ValueState<double[]> latState;

    @Override
    public void open(Configuration parameters) {
        lngState = getRuntimeContext().getState(new ValueStateDescriptor<>("kalmanLng", double[].class));
        latState = getRuntimeContext().getState(new ValueStateDescriptor<>("kalmanLat", double[].class));
    }

    @Override
    public void processElement(GpsPoint point, Context ctx, Collector<GpsPoint> out) throws Exception {
        double[] lng = update(lngState, point.getLng());
        double[] lat = update(latState, point.getLat());

        out.collect(GpsPoint.builder()
                .bikeId(point.getBikeId())
                .district(point.getDistrict())
                .lng(lng[0])
                .lat(lat[0])
                .speed(point.getSpeed())
                .direction(point.getDirection())
                .locked(point.isLocked())
                .ts(point.getTs())
                .build());
    }

    /** 预测 + 修正一步，状态存回 Flink keyed state */
    private double[] update(ValueState<double[]> state, double measurement) throws Exception {
        double[] s = state.value();
        double x;
        double p;
        if (s == null) {
            x = measurement;
            p = 1e-6;
        } else {
            x = s[0];
            p = s[1] + PROCESS_NOISE;
        }
        double k = p / (p + MEASUREMENT_NOISE);
        x = x + k * (measurement - x);
        p = (1 - k) * p;
        double[] next = {x, p};
        state.update(next);
        return next;
    }
}
