package com.bike.monitor.server.service;

import com.bike.monitor.common.geo.GeoUtil;
import com.bike.monitor.server.dto.TracePoint;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 轨迹相似度四算法（题 3，自实现）：DTW / 离散 Fréchet / LCSS / EDR。
 * 点间距离均用 Haversine 球面距离（米）。复杂度均为 O(n×m)。
 */
@Service
public class TrajectorySimilarityService {

    /** DTW：时间轴扭曲下的最小累积距离（米，越小越相似） */
    public double dtw(List<TracePoint> a, List<TracePoint> b) {
        int n = a.size();
        int m = b.size();
        double[][] dp = new double[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            java.util.Arrays.fill(dp[i], Double.MAX_VALUE / 4);
        }
        dp[0][0] = 0;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                double cost = dist(a.get(i - 1), b.get(j - 1));
                dp[i][j] = cost + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
        return dp[n][m] / (n + m);
    }

    /** 离散 Fréchet：牵狗模型（考虑时序的最小链距离，米） */
    public double frechet(List<TracePoint> a, List<TracePoint> b) {
        int n = a.size();
        int m = b.size();
        double[][] dp = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double cost = dist(a.get(i), b.get(j));
                if (i == 0 && j == 0) {
                    dp[i][j] = cost;
                } else if (i == 0) {
                    dp[i][j] = Math.max(dp[i][j - 1], cost);
                } else if (j == 0) {
                    dp[i][j] = Math.max(dp[i - 1][j], cost);
                } else {
                    dp[i][j] = Math.max(Math.min(dp[i - 1][j], Math.min(dp[i - 1][j - 1], dp[i][j - 1])), cost);
                }
            }
        }
        return dp[n - 1][m - 1];
    }

    /**
     * LCSS：允许噪声点的最长公共子序列占比（0~1，越大越相似）。
     * @param epsilonMeters 点距离阈值
     * @param deltaIdx      时序错位容忍（索引差上限）
     */
    public double lcss(List<TracePoint> a, List<TracePoint> b, double epsilonMeters, int deltaIdx) {
        int n = a.size();
        int m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (Math.abs(i - j) > deltaIdx) {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                } else if (dist(a.get(i - 1), b.get(j - 1)) <= epsilonMeters) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return (double) dp[n][m] / Math.min(n, m);
    }

    /** EDR：编辑距离（不匹配点数，越小越相似；阈值内视为匹配） */
    public int edr(List<TracePoint> a, List<TracePoint> b, double epsilonMeters) {
        int n = a.size();
        int m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int subCost = dist(a.get(i - 1), b.get(j - 1)) <= epsilonMeters ? 0 : 1;
                dp[i][j] = Math.min(dp[i - 1][j - 1] + subCost,
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1));
            }
        }
        return dp[n][m];
    }

    private double dist(TracePoint p1, TracePoint p2) {
        return GeoUtil.haversine(p1.getLng(), p1.getLat(), p2.getLng(), p2.getLat());
    }
}
