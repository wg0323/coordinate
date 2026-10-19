import { http, unwrap } from './http'
import type { TraceResult } from '../types'

/** 轨迹查询：时间单位毫秒，threshold 单位为度（0.00005 ≈ 5m） */
export function fetchTrace(bikeId: string, from: number, to: number, threshold = 0.00005, limit = 20000) {
  return unwrap<TraceResult>(http.get('/trace', { params: { bikeId, from, to, threshold, limit } }))
}
