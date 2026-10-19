import type { TracePoint } from '../types'

export const TRACE_COLORS = {
  slow: '#38bdf8',
  mid: '#00ff88',
  fast: '#ffa502',
  parked: '#a8b3c1'
} as const

export type SpeedLevel = 'slow' | 'mid' | 'fast'

export interface TraceRun {
  kind: 'ride' | 'park'
  points: TracePoint[]
}

export interface TraceStats {
  distanceKm: number
  ridingMinutes: number
  avgKmh: number
  maxKmh: number
}

/** 两点球面距离（米） */
export function haversineM(lng1: number, lat1: number, lng2: number, lat2: number): number {
  const rad = Math.PI / 180
  const dLat = (lat2 - lat1) * rad
  const dLng = (lng2 - lng1) * rad
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2
  return 2 * 6371000 * Math.asin(Math.sqrt(a))
}

/** 按速度连续性切分骑行段与停放段（阈值 0.05 m/s 过滤静止抖动） */
export function splitSegments(points: TracePoint[]): TraceRun[] {
  const runs: TraceRun[] = []
  for (const p of points) {
    const kind: TraceRun['kind'] = p.speed > 0.05 ? 'ride' : 'park'
    const last = runs[runs.length - 1]
    if (last && last.kind === kind) {
      last.points.push(p)
    } else {
      runs.push({ kind, points: [p] })
    }
  }
  return runs
}

export function levelOf(kmh: number): SpeedLevel {
  if (kmh < 10) return 'slow'
  if (kmh < 14) return 'mid'
  return 'fast'
}

export function colorOf(level: SpeedLevel): string {
  return TRACE_COLORS[level]
}

/** 每个点取前后各 2 点的平均速度（m/s → km/h）后分档 */
export function smoothedLevels(points: TracePoint[]): SpeedLevel[] {
  const n = points.length
  const levels: SpeedLevel[] = new Array(n)
  for (let i = 0; i < n; i++) {
    let sum = 0
    let count = 0
    for (let j = Math.max(0, i - 2); j <= Math.min(n - 1, i + 2); j++) {
      sum += points[j].speed
      count++
    }
    levels[i] = levelOf((sum / count) * 3.6)
  }
  return levels
}

/** 统计：总里程、骑行时长、平均/最高速度 */
export function buildTraceStats(points: TracePoint[]): TraceStats {
  let distanceM = 0
  let ridingMs = 0
  let maxMs = 0
  for (let i = 0; i < points.length; i++) {
    if (i > 0) {
      distanceM += haversineM(points[i - 1].lng, points[i - 1].lat, points[i].lng, points[i].lat)
      if (points[i].speed > 0.05) {
        ridingMs += points[i].ts - points[i - 1].ts
      }
    }
    maxMs = Math.max(maxMs, points[i].speed)
  }
  return {
    distanceKm: distanceM / 1000,
    ridingMinutes: ridingMs / 60000,
    avgKmh: ridingMs > 0 ? (distanceM / (ridingMs / 1000)) * 3.6 : 0,
    maxKmh: maxMs * 3.6
  }
}

/** 按时间线性插值出车辆位置 */
export function positionAt(points: TracePoint[], ts: number): { lng: number; lat: number; speed: number } {
  if (!points.length) return { lng: 0, lat: 0, speed: 0 }
  const first = points[0]
  const last = points[points.length - 1]
  if (ts <= first.ts) return { lng: first.lng, lat: first.lat, speed: first.speed }
  if (ts >= last.ts) return { lng: last.lng, lat: last.lat, speed: last.speed }
  let lo = 0
  let hi = points.length - 1
  while (lo + 1 < hi) {
    const mid = (lo + hi) >> 1
    if (points[mid].ts <= ts) lo = mid
    else hi = mid
  }
  const a = points[lo]
  const b = points[hi]
  const t = b.ts === a.ts ? 0 : (ts - a.ts) / (b.ts - a.ts)
  return {
    lng: a.lng + (b.lng - a.lng) * t,
    lat: a.lat + (b.lat - a.lat) * t,
    speed: b.speed
  }
}

export function traceBounds(points: TracePoint[]) {
  let minLng = Infinity
  let minLat = Infinity
  let maxLng = -Infinity
  let maxLat = -Infinity
  for (const p of points) {
    minLng = Math.min(minLng, p.lng)
    minLat = Math.min(minLat, p.lat)
    maxLng = Math.max(maxLng, p.lng)
    maxLat = Math.max(maxLat, p.lat)
  }
  return { minLng, minLat, maxLng, maxLat }
}
