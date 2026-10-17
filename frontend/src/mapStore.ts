import L from 'leaflet'

/** 全局共享的 Leaflet 地图实例（各图层组件通过 mapStore 访问） */
let map: L.Map | null = null

let resolveReady: (m: L.Map) => void = () => {}
const ready = new Promise<L.Map>(resolve => {
  resolveReady = resolve
})

export function setMap(m: L.Map): void {
  map = m
  resolveReady(m)
}

export function getMap(): L.Map {
  if (!map) {
    throw new Error('map not initialized')
  }
  return map
}

/** 等待地图初始化完成（解决子组件挂载顺序早于 MapView 的时序问题） */
export function getMapAsync(): Promise<L.Map> {
  return map ? Promise.resolve(map) : ready
}
