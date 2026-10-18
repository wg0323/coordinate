import { http, unwrap } from './http'
import type { FenceStatsDto, StatsDto } from '../types/stats'

/** 车辆统计概览（大屏核心指标） */
export function fetchStats() {
  return unwrap<StatsDto>(http.get('/stats'))
}

/** 围栏状态统计（30s 定时任务缓存） */
export function fetchFenceStats() {
  return unwrap<FenceStatsDto>(http.get('/fence/stats'))
}
