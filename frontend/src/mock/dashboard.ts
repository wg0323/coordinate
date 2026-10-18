/** 大屏静态数据源（无真实链路的模块统一走这里） */

/** 运维调度车辆（静态占位层） */
export interface OpsVehicle {
  id: string
  lng: number
  lat: number
  label: string
}

export const OPS_VEHICLES: OpsVehicle[] = [
  { id: 'OP-001', lng: 116.46, lat: 39.91, label: '国贸片区调度' },
  { id: 'OP-002', lng: 116.32, lat: 39.98, label: '中关村片区调度' },
  { id: 'OP-003', lng: 116.36, lat: 39.9, label: '西单片区调度' },
  { id: 'OP-004', lng: 116.43, lat: 39.87, label: '方庄片区调度' },
  { id: 'OP-005', lng: 116.49, lat: 39.92, label: '望京片区调度' },
  { id: 'OP-006', lng: 116.28, lat: 39.86, label: '丽泽片区调度' }
]

/** 地图中央悬浮条：今日订单（静态口径） */
export const TODAY_ORDERS = 287456
