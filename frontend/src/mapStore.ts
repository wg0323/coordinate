import L from 'leaflet'

/** 全局共享的 Leaflet 地图实例（各图层组件通过 mapStore 访问） */
let map: L.Map | null = null

let resolveReady: ((m: L.Map) => void) | null = null
let ready: Promise<L.Map> | null = null

export function setMap(m: L.Map): void {
  map = m
  resolveReady?.(m)
  resolveReady = null
}

/** 地图销毁时清空共享引用，下一次 getMapAsync 会等待新地图就绪，避免图层挂到已销毁的实例 */
export function clearMap(m: L.Map): void {
  if (map !== m) {
    return
  }
  map = null
  ready = null
  resolveReady = null
}

/** 等待地图初始化完成（解决子组件挂载顺序早于 MapView 的时序问题） */
export function getMapAsync(): Promise<L.Map> {
  if (map) {
    return Promise.resolve(map)
  }
  if (!ready) {
    ready = new Promise<L.Map>(resolve => {
      resolveReady = resolve
    })
  }
  return ready
}
