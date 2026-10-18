/** 与后端 StatsDto/DistrictCountDto 对齐 */
export interface StatsDto {
  total: number
  online: number
  riding: number
  idle: number
  offline: number
  onlineRate: number
  districtCounts: DistrictCountDto[]
}

export interface DistrictCountDto {
  name: string
  count: number
  riding: number
}

/** 围栏统计（Task 5 实现 /api/fence/stats 后生效） */
export interface FenceStatsDto {
  total: number
  normal: number
  nearFull: number
  overflow: number
  empty: number
  noCapacity: number
  avgLoadRate: number
  overflowed: FenceCellStatDto[]
}

export interface FenceCellStatDto {
  fenceId: number | null
  name: string
  capacity: number | null
  count: number
  loadRate: number
}
