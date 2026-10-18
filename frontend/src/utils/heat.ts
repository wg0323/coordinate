/** 热力图层共享参数：色阶与图例同源，避免两处走样 */

/** 固定色阶（稀疏 → 稠密）：绿色占前 40%，黄色调淡并后移到 60% */
export const HEAT_GRADIENT: Record<number, string> = {
  0.0: '#01eb81',
  0.4: '#bcee7d',
  0.6: '#e8f2a3',
  0.8: '#fa781a',
  1.0: '#ae000a'
}

/** 图例渐变条背景：按偏移量升序并带百分比输出，保证图例位置与色阶一致
 *（对象里的 "0"/"1" 是整数键，会被 JS 重排到最前，必须显式排序） */
export const HEAT_LEGEND_CSS = `linear-gradient(90deg, ${Object.entries(HEAT_GRADIENT)
  .sort((a, b) => Number(a[0]) - Number(b[0]))
  .map(([at, color]) => `${color} ${Number(at) * 100}%`)
  .join(', ')})`

/** 核密度参数：半径/模糊为像素；max 为颜色饱和的强度上限
 *（leaflet.heat 每点权重按 1/2^(maxZoom-zoom) 递减，max 取固定值 → 同一密度永远同一颜色） */
export const HEAT_RADIUS = 18
export const HEAT_BLUR = 15
export const HEAT_MAX = 0.4
