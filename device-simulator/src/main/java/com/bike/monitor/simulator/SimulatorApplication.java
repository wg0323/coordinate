package com.bike.monitor.simulator;

import com.bike.monitor.simulator.config.SimConfig;
import com.bike.monitor.simulator.engine.SimEngine;
import com.bike.monitor.simulator.generator.FenceParkingPlanner;
import com.bike.monitor.simulator.generator.FleetGenerator;
import com.bike.monitor.simulator.generator.RideGenerator;
import com.bike.monitor.simulator.model.Bike;
import com.bike.monitor.simulator.noise.DriftInjector;
import com.bike.monitor.simulator.report.Reporter;
import com.bike.monitor.simulator.scenario.CompanionScenario;
import com.bike.monitor.simulator.store.FenceStore;
import com.bike.monitor.simulator.store.FleetStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 设备模拟器入口。
 * 示例：java -jar device-simulator.jar --kafka=192.168.1.25:9092 --speed-factor=20 --vehicle-ratio=0.1
 *
 * <p>车队台账存于 MongoDB（bikes 集合）：首次启动 seed 全量 84,840 辆，
 * 之后每次启动从库中加载，再按 --vehicle-ratio 抽样出本次活跃车辆。</p>
 */
public class SimulatorApplication {

    private static final Logger log = LoggerFactory.getLogger(SimulatorApplication.class);

    public static void main(String[] args) throws Exception {
        SimConfig config = SimConfig.parse(args);
        log.info("模拟器启动：kafka={}, topic={}, mongo={}, speedFactor={}, vehicleRatio={}, driftRate={}, companionPairs={}, fenceParked={}",
                config.getKafkaServers(), config.getTopic(), config.getMongoUri(),
                config.getSpeedFactor(), config.getVehicleRatio(), config.getDriftRate(),
                config.getCompanionPairs(), config.getFenceParked());

        List<Bike> fleet = loadFleet(config);
        relocateToFences(fleet, config);

        CompanionScenario companion = new CompanionScenario();
        companion.bind(fleet, config.getCompanionPairs(), 42L);

        RideGenerator rideGenerator = new RideGenerator(42L);
        DriftInjector driftInjector = new DriftInjector(config.getDriftRate(), 42L);
        Reporter reporter = new Reporter(config.getKafkaServers(), config.getTopic());

        SimEngine engine = new SimEngine(fleet, companion, rideGenerator,
                driftInjector, reporter, config);
        Thread engineThread = new Thread(engine, "sim-engine");
        engineThread.start();

        // TPS 统计日志：每 5s 真实时间输出一次
        ScheduledExecutorService statExecutor = Executors.newSingleThreadScheduledExecutor();
        statExecutor.scheduleAtFixedRate(() -> log.info("已发送 {} 条（失败 {}），TPS≈{}/s",
                        reporter.getSent(), reporter.getFailed(), reporter.getSent() / 5),
                5, 5, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            engine.stop();
            statExecutor.shutdownNow();
            reporter.close();
            log.info("模拟器退出，共发送 {} 条", reporter.getSent());
        }));

        engineThread.join();
        log.info("模拟结束，共发送 {} 条", reporter.getSent());
        reporter.close();
        statExecutor.shutdownNow();
    }

    /** 车队来源：Mongo bikes 集合（首次启动自动 seed 全量），再按 vehicleRatio 抽样活跃车辆 */
    private static List<Bike> loadFleet(SimConfig config) {
        List<Bike> fleet;
        try (FleetStore store = new FleetStore(config.getMongoUri())) {
            if (store.count() == 0) {
                fleet = new FleetGenerator(42L).generate();
                store.saveAll(fleet);
                log.info("车队台账为空，已 seed 入库 {} 辆（--mongo={}）", fleet.size(), config.getMongoUri());
            } else {
                fleet = store.loadAll();
                log.info("从 Mongo 加载车队台账：{} 辆", fleet.size());
            }
        }
        fleet = sample(fleet, config.getVehicleRatio());
        log.info("本次活跃车辆：{} 辆（vehicle-ratio={}）", fleet.size(), config.getVehicleRatio());
        return fleet;
    }

    /** 把部分活跃车的常驻点迁入围栏，使围栏内停放数量可见（--fence-parked=0 关闭） */
    private static void relocateToFences(List<Bike> fleet, SimConfig config) {
        if (config.getFenceParked() <= 0) {
            return;
        }
        try (FenceStore store = new FenceStore(config.getMongoUri())) {
            new FenceParkingPlanner(42L).assign(fleet, store.loadAll(), config.getFenceParked());
        } catch (Exception e) {
            log.warn("围栏常驻点分配失败，保持原常驻点：{}", e.getMessage());
        }
    }

    /** 固定种子按序抽样：同一份数据每次启动抽中的车辆完全一致，模拟时钟/事件序列可复现 */
    private static List<Bike> sample(List<Bike> fleet, double ratio) {
        if (ratio >= 1.0) {
            return fleet;
        }
        Random random = new Random(42L);
        List<Bike> active = new ArrayList<>();
        for (Bike bike : fleet) {
            if (random.nextDouble() < ratio) {
                active.add(bike);
            }
        }
        return active;
    }
}
