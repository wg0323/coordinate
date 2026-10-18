/** 与后端 DTO 对齐的类型定义 */

export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

export interface NearbyBike {
  bikeId: string
  distM: number
  lng: number
  lat: number
  /** riding | idle | unknown */
  status?: string
  /** 是否位于任一电子围栏内（/points/bbox 标注；字段缺失视为未知） */
  inFence?: boolean
}

export interface SimilarityResult {
  bikeA: string
  bikeB: string
  pointsA: number
  pointsB: number
  dtw: number
  frechet: number
  lcss: number
  edr: number
}

export interface FenceDto {
  fenceId: number | null
  name: string
  coordinates: [number, number][]
}

export interface FenceAlert {
  fenceId: number
  fenceName: string
  bikeId: string
  event: 'ENTER' | 'EXIT'
  lng: number
  lat: number
  ts: number
}
