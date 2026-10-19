package com.bike.monitor.simulator.config;

import lombok.Getter;

/**
 * 模拟器命令行参数。格式：--key=value 或 --key value
 */
@Getter
public class SimConfig {

    /** Kafka 在虚拟机 192.168.1.25（与中间件统一环境），本地不再有 broker */
    private String kafkaServers = "192.168.1.25:9092";
    private String topic = "bike-gps-raw";
    /** 车队台账 MongoDB：首次启动 seed 全量车队，此后从库中加载 */
    private String mongoUri = "mongodb://192.168.1.25:27017/bike_monitor";
    /** 时间加速倍数：60 = 1 真实秒 ≈ 1 模拟分钟（默认值便于演示快速出数据） */
    private double speedFactor = 60;
    /** 活跃车辆抽样比例（从 Mongo 全量车队台账中抽样，固定种子保证跨重启稳定） */
    private double vehicleRatio = 1.0;
    /** 脏数据注入概率（每次上报） */
    private double driftRate = 0.02;
    /** 伴随车辆对数（题 3 套牌车演示） */
    private int companionPairs = 2;
    /** 目标：围栏内停放车辆数（按约 64% 停放比例反推需迁入围栏的活跃车；0 = 不迁入） */
    private int fenceParked = 3000;
    /** 运行真实秒数，0 = 一直运行 */
    private long durationSec = 0;
    /** 模拟时钟持久化文件：跨次启动保持 ts 单调连续（避免下游 watermark 被回退的 ts 钉死） */
    private String clockFile = "data/sim-clock.txt";
    /** 运行模式：空=常规模拟；history-trace=只生成固定设备一天历史轨迹 */
    private String mode = "";
    private String traceBike = "BMXC000123";
    private String traceDistrict = "中心城区";
    /** 1=昨天，0=今天 */
    private int traceDayOffset = 1;
    private int traceSegments = 5;
    private long traceSeed = 42L;

    public static SimConfig parse(String[] args) {
        SimConfig config = new SimConfig();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String key;
            String value;
            if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if (eq > 0) {
                    key = arg.substring(2, eq);
                    value = arg.substring(eq + 1);
                } else {
                    key = arg.substring(2);
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("参数缺少值: " + arg);
                    }
                    value = args[++i];
                }
                config.apply(key, value);
            }
        }
        return config;
    }

    private void apply(String key, String value) {
        switch (key) {
            case "kafka":
                kafkaServers = value;
                break;
            case "topic":
                topic = value;
                break;
            case "mongo":
                mongoUri = value;
                break;
            case "speed-factor":
                speedFactor = Double.parseDouble(value);
                break;
            case "vehicle-ratio":
                vehicleRatio = Double.parseDouble(value);
                break;
            case "drift-rate":
                driftRate = Double.parseDouble(value);
                break;
            case "companion-pairs":
                companionPairs = Integer.parseInt(value);
                break;
            case "duration-sec":
                durationSec = Long.parseLong(value);
                break;
            case "fence-parked":
                fenceParked = Integer.parseInt(value);
                break;
            case "clock-file":
                clockFile = value;
                break;
            case "mode":
                mode = value;
                break;
            case "trace-bike":
                traceBike = value;
                break;
            case "trace-district":
                traceDistrict = value;
                break;
            case "trace-day-offset":
                traceDayOffset = Integer.parseInt(value);
                break;
            case "trace-segments":
                traceSegments = Integer.parseInt(value);
                break;
            case "trace-seed":
                traceSeed = Long.parseLong(value);
                break;
            default:
                throw new IllegalArgumentException("未知参数: --" + key);
        }
    }
}
