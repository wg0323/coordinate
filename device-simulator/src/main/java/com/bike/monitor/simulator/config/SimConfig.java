package com.bike.monitor.simulator.config;

import lombok.Getter;

/**
 * 模拟器命令行参数。格式：--key=value 或 --key value
 */
@Getter
public class SimConfig {

    private String kafkaServers = "localhost:9092";
    private String topic = "bike-gps-raw";
    /** 时间加速倍数：60 = 1 真实秒 ≈ 1 模拟分钟（默认值便于演示快速出数据） */
    private double speedFactor = 60;
    /** 车辆比例（在 1:10 基础上再缩放） */
    private double vehicleRatio = 1.0;
    /** 脏数据注入概率（每次上报） */
    private double driftRate = 0.02;
    /** 伴随车辆对数（题 3 套牌车演示） */
    private int companionPairs = 2;
    /** 运行真实秒数，0 = 一直运行 */
    private long durationSec = 0;
    /** 模拟时钟持久化文件：跨次启动保持 ts 单调连续（避免下游 watermark 被回退的 ts 钉死） */
    private String clockFile = "data/sim-clock.txt";

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
            case "clock-file":
                clockFile = value;
                break;
            default:
                throw new IllegalArgumentException("未知参数: --" + key);
        }
    }
}
