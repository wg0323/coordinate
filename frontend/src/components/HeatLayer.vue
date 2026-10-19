<template><span></span></template>

<script setup lang="ts">
import { onUnmounted, watch } from 'vue'
import L from 'leaflet'
import 'leaflet.heat'
import { getMapAsync } from '../mapStore'
import { HEAT_BLUR, HEAT_GRADIENT, HEAT_MAX, HEAT_RADIUS } from '../utils/heat'
import type { NearbyBike } from '../types'

const props = defineProps<{ bikes: NearbyBike[] }>()
const enabled = defineModel<boolean>('enabled', { default: true })

/** 低缩放时全城车辆挤在少数像素上，缩小半径并单独设定饱和阈值，避免整片糊成红色 */
const FAR_RADIUS = 12
const FAR_BLUR = 10
const FAR_MAX = 0.25

let map: L.Map | null = null
let heat: L.HeatLayer | null = null
let disposed = false

/** 热力数据：车辆实时坐标，每辆车权重 1 */
function toPoints(bikes: NearbyBike[]): Array<[number, number]> {
  return bikes.map(b => [b.lat, b.lng])
}

function tuneForZoom(): void {
  if (!heat || !map) {
    return
  }
  const far = map.getZoom() <= 10
  heat.setOptions({
    radius: far ? FAR_RADIUS : HEAT_RADIUS,
    blur: far ? FAR_BLUR : HEAT_BLUR,
    max: far ? FAR_MAX : HEAT_MAX
  })
}

function detach(): void {
  heat?.remove()
  heat = null
  map?.off('zoomend', tuneForZoom)
}

// 数据或开关变化时更新：保留实例做增量重绘，避免整层移除重建造成闪断
watch(
  [() => props.bikes, () => enabled.value],
  async ([bikes]) => {
    if (!map) {
      map = await getMapAsync()
    }
    if (disposed) {
      return
    }
    if (!enabled.value || bikes.length === 0) {
      detach()
      return
    }
    if (!heat) {
      const layer = L.heatLayer(toPoints(bikes), {
        // 固定色阶与饱和阈值：颜色只随真实密度变化，不随每批最大车数整体漂移
        gradient: HEAT_GRADIENT,
        max: HEAT_MAX,
        radius: HEAT_RADIUS,
        blur: HEAT_BLUR,
        minOpacity: 0.002
      })
      // leaflet.heat 的 rAF 重绘帧可能在图层移除后才执行，此时 _map 已置空；
      // 包装 _redraw 成安全空操作，避免插件在已销毁地图上取尺寸报错
      const redraw = layer._redraw
      layer._redraw = () => {
        if ((layer as unknown as { _map?: L.Map | null })._map) {
          redraw.call(layer)
        }
      }
      heat = layer.addTo(map)
      map.on('zoomend', tuneForZoom)
      tuneForZoom()
      return
    }
    heat.setLatLngs(toPoints(bikes))
  },
  { immediate: true }
)

onUnmounted(() => {
  disposed = true
  detach()
})
</script>
