package com.bike.monitor.simulator;

import com.bike.monitor.simulator.config.SimConfig;
import com.bike.monitor.simulator.engine.SimEngine;
import com.bike.monitor.simulator.generator.FleetGenerator;
import com.bike.monitor.simulator.generator.RideGenerator;
import com.bike.monitor.simulator.model.Bike;
import com.bike.monitor.simulator.noise.DriftInjector;
import com.bike.monitor.simulator.report.Reporter;
import com.bike.monitor.simulator.scenario.CompanionScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 设备模拟器入口。
 * 示例：java -jar device-simulator.jar --speed-factor=60 --vehicle-ratio=1 --duration-sec=120
 */
public class SimulatorApplication {

    private static final Logger log = LoggerFactory.getLogger(SimulatorApplication.class);

    public static void main(String[] args) throws Exception {
        SimConfig config = SimConfig.parse(args);
        log.info("模拟器启动：kafka={}, topic={}, speedFactor={}, vehicleRatio={}, driftRate={}, companionPairs={}",
                config.getKafkaServers(), config.getTopic(), config.getSpeedFactor(),
                config.getVehicleRatio(), config.getDriftRate(), config.getCompanionPairs());

        FleetGenerator fleetGenerator = new FleetGenerator(config.getVehicleRatio(), 42L);
        List<Bike> fleet = fleetGenerator.generate();
        log.info("车队规模：{} 辆", fleet.size());

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
}
