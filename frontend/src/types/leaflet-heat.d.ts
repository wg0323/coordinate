import 'leaflet'

/** leaflet.heat 无官方类型，最小声明 */
declare module 'leaflet.heat'

declare module 'leaflet' {
  function heatLayer(
    latlngs: Array<[number, number, number?]>,
    options?: Record<string, unknown>
  ): L.Layer
}
