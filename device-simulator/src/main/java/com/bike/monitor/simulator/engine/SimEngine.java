package com.bike.monitor.simulator.engine;

import com.bike.monitor.common.dto.GpsPoint;
import com.bike.monitor.simulator.config.SimConfig;
import com.bike.monitor.simulator.generator.RideGenerator;
import com.bike.monitor.simulator.model.Bike;
import com.bike.monitor.simulator.model.Ride;
import com.bike.monitor.simulator.noise.DriftInjector;
import com.bike.monitor.simulator.report.Reporter;
import com.bike.monitor.simulator.scenario.CompanionScenario;

import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * 事件驱动模拟引擎：以事件队列推进模拟时钟（simMs = realMs × speedFactor），
 * 事件间的时间差与位置差保持物理一致（相邻点按模拟时间差计算速度不超自行车极限）。
 *
 * <p>事件类型：RIDE_START（泊松到达，均值=86400s/周转率）、REPORT（骑行中 5s/50m 双触发）、
 * RIDE_END、HEARTBEAT（静止车 10min 心跳）。</p>
 */
public class SimEngine implements Runnable {

    private enum Type { RIDE_START, REPORT, RIDE_END, HEARTBEAT }

    private static final long REPORT_INTERVAL_MS = 5_000;
    private static final double REPORT_DISTANCE_M = 50;
    private static final long HEARTBEAT_INTERVAL_MS = 600_000;

    private static class TimedEvent implements Comparable<TimedEvent> {
        final long simTs;
        final Bike bike;
        final Type type;
        final int generation;
        final Ride ride;

        TimedEvent(long simTs, Bike bike, Type type, int generation, Ride ride) {
            this.simTs = simTs;
            this.bike = bike;
            this.type = type;
            this.generation = generation;
            this.ride = ride;
        }

        @Override
        public int compareTo(TimedEvent o) {
            return Long.compare(simTs, o.simTs);
        }
    }

    private final List<Bike> fleet;
    private final CompanionScenario companion;
    private final RideGenerator rideGenerator;
    private final DriftInjector driftInjector;
    private final Reporter reporter;
    private final SimConfig config;
    private final Random random = new Random(42L);

    private final PriorityQueue<TimedEvent> queue = new PriorityQueue<>();
    private final long baseEpochMs = System.currentTimeMillis();
    /** 跨次启动的模拟时钟偏移（持久化于 clockFile，保证 ts 单调连续） */
    private final long initialSimOffsetMs;
    private volatile boolean running = true;
    private long realStartMs;
    private long lastClockSaveRealMs;

    public SimEngine(List<Bike> fleet, CompanionScenario companion,
                     RideGenerator rideGenerator, DriftInjector driftInjector,
                     Reporter reporter, SimConfig config) {
        this.fleet = fleet;
        this.companion = companion;
        this.rideGenerator = rideGenerator;
        this.driftInjector = driftInjector;
        this.reporter = reporter;
        this.config = config;
        this.initialSimOffsetMs = loadSimOffset();
    }

    private long loadSimOffset() {
        try {
            java.io.File f = new java.io.File(config.getClockFile());
            if (f.exists()) {
                return Long.parseLong(new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8").trim());
            }
        } catch (Exception e) {
            // 时钟文件损坏则从 0 开始
        }
        return 0;
    }

    /** 周期持久化模拟时钟偏移（相对 baseEpoch 的模拟毫秒数） */
    private void saveSimOffset(long simNow) {
        try {
            java.io.File f = new java.io.File(config.getClockFile());
            if (f.getParentFile() != null) {
                f.getParentFile().mkdirs();
            }
            java.nio.file.Files.write(f.toPath(), String.valueOf(simNow).getBytes("UTF-8"));
            lastClockSaveRealMs = System.currentTimeMillis();
        } catch (Exception e) {
            // 持久化失败仅影响下次启动的时钟连续性
        }
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        realStartMs = System.currentTimeMillis();
        scheduleInitialEvents();
        try {
            loop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void scheduleInitialEvents() {
        for (Bike bike : fleet) {
            // 跟骑车不自主骑行，仅跟随领骑车（由 RIDE_START 处理器劫持调度）
            if (companion.isFollower(bike)) {
                continue;
            }
            queue.add(new TimedEvent(rideGenerator.nextRideStartGapMs(bike), bike, Type.RIDE_START,
                    bike.getGeneration(), null));
            // 首次心跳随机错开，避免 8.4 万心跳同时爆发
            queue.add(new TimedEvent(random.nextInt((int) HEARTBEAT_INTERVAL_MS), bike,
                    Type.HEARTBEAT, bike.getGeneration(), null));
        }
    }

    private void loop() throws InterruptedException {
        while (running) {
            long simNow = initialSimOffsetMs
                    + (long) ((System.currentTimeMillis() - realStartMs) * config.getSpeedFactor());
            if (System.currentTimeMillis() - lastClockSaveRealMs > 5_000) {
                saveSimOffset(simNow);
            }
            TimedEvent event;
            while (running && (event = queue.poll()) != null) {
                if (event.simTs > simNow) {
                    queue.add(event);
                    break;
                }
                if (event.bike.getGeneration() != event.generation) {
                    continue;
                }
                handle(event, simNow);
            }
            if (config.getDurationSec() > 0
                    && System.currentTimeMillis() - realStartMs > config.getDurationSec() * 1000) {
                return;
            }
            Thread.sleep(20);
        }
    }

    private void handle(TimedEvent event, long simNow) {
        Bike bike = event.bike;
        switch (event.type) {
            case RIDE_START:
                handleRideStart(event, simNow);
                break;
            case REPORT:
                handleReport(event);
                break;
            case RIDE_END:
                bike.setCurrentRide(null);
                scheduleRideStart(bike, event.simTs + rideGenerator.nextRideStartGapMs(bike));
                queue.add(new TimedEvent(event.simTs + HEARTBEAT_INTERVAL_MS, bike,
                        Type.HEARTBEAT, bike.getGeneration(), null));
                break;
            case HEARTBEAT:
                if (!bike.isRiding()) {
                    send(bike, bike.getHomeLng() + (random.nextDouble() - 0.5) * 0.0005,
                            bike.getHomeLat() + (random.nextDouble() - 0.5) * 0.0005,
                            0, 0, event.simTs);
                    queue.add(new TimedEvent(event.simTs + HEARTBEAT_INTERVAL_MS, bike,
                            Type.HEARTBEAT, bike.getGeneration(), null));
                }
                break;
            default:
        }
    }

    private void handleRideStart(TimedEvent event, long simNow) {
        Bike bike = event.bike;
        if (bike.isRiding()) {
            return;
        }
        Ride ride = rideGenerator.randomRide(bike, event.simTs);
        bike.setCurrentRide(ride);
        // 骑行结束后的下一次骑行
        scheduleRideStart(bike, ride.endSimMs() + rideGenerator.nextRideStartGapMs(bike));
        // 首个上报点（起点）
        queue.add(new TimedEvent(Math.max(event.simTs, simNow), bike, Type.REPORT,
                bike.getGeneration(), ride));

        // 伴随场景：跟骑车 2s 后沿同一路径出发（题 3 套牌车演示）
        Bike follower = companion.followerOf(bike);
        if (follower != null && !follower.isRiding()) {
            follower.setGeneration(follower.getGeneration() + 1);
            Ride followerRide = rideGenerator.companionRide(ride, follower.getBikeId(),
                    ride.getStartSimMs() + 2_000);
            follower.setCurrentRide(followerRide);
            queue.add(new TimedEvent(Math.max(followerRide.getStartSimMs(), simNow), follower,
                    Type.REPORT, follower.getGeneration(), followerRide));
        }
    }

    private void handleReport(TimedEvent event) {
        Ride ride = event.ride;
        if (event.bike.getCurrentRide() != ride) {
            return;
        }
        double[] pos = ride.positionAt(event.simTs);
        // 速度加小幅噪声，航向为骑行方位角
        double speed = ride.getSpeedMps() * (0.9 + random.nextDouble() * 0.2);
        send(event.bike, pos[0], pos[1], speed, ride.getBearingDeg(), event.simTs);

        // 双触发：时间 5s 或距离 50m，取先到者
        long byTime = event.simTs + REPORT_INTERVAL_MS;
        long byDistance = event.simTs + (long) (REPORT_DISTANCE_M / ride.getSpeedMps() * 1000);
        long next = Math.min(byTime, byDistance);
        if (next >= ride.endSimMs()) {
            queue.add(new TimedEvent(ride.endSimMs(), event.bike, Type.RIDE_END,
                    event.bike.getGeneration(), null));
        } else {
            queue.add(new TimedEvent(next, event.bike, Type.REPORT,
                    event.bike.getGeneration(), ride));
        }
    }

    private void scheduleRideStart(Bike bike, long simTs) {
        queue.add(new TimedEvent(simTs, bike, Type.RIDE_START, bike.getGeneration(), null));
    }

    /** ts = 引擎基准 epoch + 模拟时间，保证相邻点距离/时间差的物理一致性 */
    private void send(Bike bike, double lng, double lat, double speed, float direction, long simTs) {
        GpsPoint point = GpsPoint.builder()
                .bikeId(bike.getBikeId())
                .district(bike.getDistrict().getName())
                .lng(lng)
                .lat(lat)
                .speed(speed)
                .direction(direction)
                .locked(speed == 0)
                .ts(baseEpochMs + simTs)
                .build();
        reporter.send(driftInjector.inject(point));
    }
}
