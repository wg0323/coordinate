package com.bike.monitor.flink.util;

import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Flink 作业环境统一配置：并行度、checkpoint、状态后端（FenceJob/H3AggJob 共用）。
 */
public final class JobSetup {

    private JobSetup() {
    }

    public static StreamExecutionEnvironment setup(ParameterTool params) {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(params.getInt("parallelism", 3));

        // checkpoint：2 分钟一次；目录挂载宿主机 volume（容错恢复演示前提）。
        // 状态后端默认 heap（本系统状态量小，heap 足够；生产大状态口径切 RocksDB），
        // --backend rocksdb 切换（生产大状态口径），checkpoint/恢复能力两者一致。
        env.enableCheckpointing(120_000, CheckpointingMode.EXACTLY_ONCE);
        if ("rocksdb".equals(params.get("backend", "heap"))) {
            env.setStateBackend(new EmbeddedRocksDBStateBackend());
        } else {
            env.setStateBackend(new HashMapStateBackend());
        }
        env.getCheckpointConfig().setCheckpointStorage(
                params.get("checkpoint-dir", "file:///opt/flink/checkpoints"));
        env.getCheckpointConfig().setExternalizedCheckpointCleanup(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        return env;
    }
}
