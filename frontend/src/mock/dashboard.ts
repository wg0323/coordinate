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

/** 实时订单流：区名 × 动作循环序列（滚动展示用） */
export const ORDER_DISTRICTS = [
  '朝阳区', '海淀区', '西城区', '东城区', '丰台区', '通州区',
  '昌平区', '大兴区', '顺义区', '房山区', '经开区', '石景山区'
]

export const ORDER_ACTIONS = ['开锁', '还车'] as const

/** 每分钟订单速率文案 */
export const ORDER_RATE_TEXT = '~856'

/** 今日骑行趋势（24 点，早晚双峰）与昨日同期 */
export const TREND_TODAY = [
  180, 120, 90, 70, 95, 320, 1450, 3200, 2600, 1900,
  1500, 1750, 2100, 1900, 1650, 1800, 2400, 3300, 3600, 2800,
  1900, 1200, 700, 350
]
export const TREND_YESTERDAY = [
  170, 115, 85, 65, 90, 300, 1350, 3000, 2450, 1800,
  1400, 1650, 1980, 1800, 1560, 1700, 2250, 3100, 3380, 2650,
  1800, 1150, 660, 330
]

/** 智能调度建议（静态） */
export interface DispatchAdvice {
  from: string
  to: string
  count: number
  revenue: number
}

export const DISPATCH_ADVICE: DispatchAdvice[] = [
  { from: '国贸', to: '望京', count: 80, revenue: 2400 },
  { from: '中关村', to: '西二旗', count: 50, revenue: 1500 },
  { from: '亦庄', to: '宋家庄', count: 30, revenue: 900 }
]

export const DISPATCH_EXECUTED = 12
export const DISPATCH_PENDING = 8

/** 底部数据带静态指标 */
export const BOTTOM_STATS = [
  { label: '今日订单', value: '287,456' },
  { label: '累计骑行', value: '1,245,678 km' },
  { label: '碳减排', value: '89.2 吨' },
  { label: '活跃用户', value: '156,789' },
  { label: '平均骑行', value: '14.3 min' },
  { label: '今日营收', value: '¥431,184' }
]

/** 跑马灯公告 */
export const TICKER_NEWS = [
  '海淀区中关村围栏扩容工程已完成，新增容量 240 位',
  '交管部门提示：工作日早高峰 7:00-9:00 地铁口周边请规范停放'
]

/** 顶部天气（静态） */
export const WEATHER_TEXT = '晴 14℃ | 空气良'
