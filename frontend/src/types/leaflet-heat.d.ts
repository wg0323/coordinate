import 'leaflet'

/** leaflet.heat 无官方类型，最小声明 */
declare module 'leaflet.heat'

declare module 'leaflet' {
  interface HeatLayerOptions {
    minOpacity?: number
    maxZoom?: number
    max?: number
    radius?: number
    blur?: number
    gradient?: Record<number, string>
    pane?: string
  }

  interface HeatLayer extends Layer {
    setLatLngs(latlngs: Array<[number, number, number?]>): this
    setOptions(options: HeatLayerOptions): this
    redraw(): this
    /** 内部重绘方法（插件用 rAF 调度，移除图层后仍可能被调用） */
    _redraw(): void
  }

  function heatLayer(
    latlngs: Array<[number, number, number?]>,
    options?: HeatLayerOptions
  ): HeatLayer
}
