package com.bike.monitor.flink.clean.filter;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.common.geo.GeoUtil;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

/**
 * 物理极限过滤（题 1 第一层）：相邻两点 Haversine 距离 / 时间差 > 自行车物理极限即判漂移剔除。
 *
 * <p>漂移点不更新参考状态（避免以漂移点为基准的连锁误判）。
 * 冷启动保护：车辆真实换电/搬迁导致长距离跳变时，连续 {@link #MAX_CONSECUTIVE_DROPS}
 * 次被拒后接受新位置并重置基准，防止永久丢点。</p>
 */
public class SpeedJumpFilter extends KeyedProcessFunction<String, GpsPoint, GpsPoint> {

    /** 物理极限 60 km/h（题面口径：瞬间移动 10km 级别必为漂移） */
    public static final double MAX_SPEED_MPS = 60.0 / 3.6;
    private static final int MAX_CONSECUTIVE_DROPS = 10;

    private transient ValueState<GpsPoint> lastPoint;
    private transient ValueState<Integer> consecutiveDrops;

    @Override
    public void open(Configuration parameters) {
        lastPoint = getRuntimeContext()
                .getState(new ValueStateDescriptor<>("lastAcceptedPoint", GpsPoint.class));
        consecutiveDrops = getRuntimeContext()
                .getState(new ValueStateDescriptor<>("consecutiveDrops", Integer.class));
    }

    @Override
    public void processElement(GpsPoint point, Context ctx, Collector<GpsPoint> out) throws Exception {
        GpsPoint prev = lastPoint.value();
        if (prev == null) {
            accept(point, out);
            return;
        }
        double dtSec = (point.getTs() - prev.getTs()) / 1000.0;
        if (dtSec <= 0) {
            // 时间倒序或重复点，直接丢弃
            return;
        }
        double distM = GeoUtil.haversine(prev.getLng(), prev.getLat(), point.getLng(), point.getLat());
        if (distM / dtSec > MAX_SPEED_MPS) {
            int drops = consecutiveDrops.value() == null ? 0 : consecutiveDrops.value();
            if (++drops >= MAX_CONSECUTIVE_DROPS) {
                // 连续多点被拒：判定为真实位置迁移，重置基准
                resetAndAccept(point, out);
            } else {
                consecutiveDrops.update(drops);
            }
            return;
        }
        accept(point, out);
    }

    private void accept(GpsPoint point, Collector<GpsPoint> out) throws Exception {
        lastPoint.update(point);
        consecutiveDrops.update(0);
        out.collect(point);
    }

    private void resetAndAccept(GpsPoint point, Collector<GpsPoint> out) throws Exception {
        lastPoint.update(point);
        consecutiveDrops.update(0);
        out.collect(point);
    }
}
