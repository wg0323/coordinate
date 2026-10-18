<template><span></span></template>

<script setup lang="ts">
import { onUnmounted, watch } from 'vue'
import L from 'leaflet'
import { getMapAsync } from '../../mapStore'
import type { NearbyBike } from '../../types'

const props = defineProps<{ bikes: NearbyBike[] }>()

// 状态着色（大屏深色滤镜下仍能区分）：围栏内停放=灰，围栏外停放=橘，骑行中=绿
// 车辆点整体缩小，避免密集区域盖住围栏与路网
const PARKED_IN_FENCE: BikeMeta = { color: '#5f6b7a', fill: '#a8b3c1', label: '围栏内停放', radius: 4, fillOpacity: 1 }
const PARKED_OUTSIDE: BikeMeta = { color: '#b37000', fill: '#ffa502', label: '围栏外停放', radius: 3, fillOpacity: 0.8 }
const RIDING: BikeMeta = { color: '#00875a', fill: '#00ff88', label: '骑行中', radius: 3.5, fillOpacity: 0.95 }
const UNKNOWN: BikeMeta = { color: '#0d47a1', fill: '#1565c0', label: '状态未知', radius: 3.5, fillOpacity: 0.85 }
/** 旧后端未回传 inFence 时的降级样式 */
const IDLE: BikeMeta = { color: '#5f6b7a', fill: '#a8b3c1', label: '空闲', radius: 4, fillOpacity: 1 }

interface BikeMeta {
  color: string
  fill: string
  label: string
  radius: number
  fillOpacity: number
}

function metaOf(b: NearbyBike): BikeMeta {
  const status = b.status ?? 'unknown'
  if (status === 'riding') {
    return RIDING
  }
  if (status === 'idle') {
    if (b.inFence === false) {
      return PARKED_OUTSIDE
    }
    if (b.inFence === true) {
      return PARKED_IN_FENCE
    }
    return IDLE
  }
  return UNKNOWN
}

interface BikeMarker {
  marker: L.CircleMarker
  meta: BikeMeta
}

let group: L.LayerGroup | null = null
/** Canvas 渲染器全生命周期复用：每次重绘新建会在地图上堆积 canvas（每块约 8MB），长时间运行必现内存打满 */
let renderer: L.Canvas | null = null
const markers = new Map<string, BikeMarker>()
let disposed = false

/** 增量更新：只增删/移动变化的车辆，避免每轮轮询重建数千 marker 造成 GC 尖峰 */
function sync(bikes: NearbyBike[]): void {
  if (!group || !renderer) {
    return
  }
  const seen = new Set<string>()
  for (const b of bikes) {
    seen.add(b.bikeId)
    const meta = metaOf(b)
    const item = markers.get(b.bikeId)
    if (item) {
      item.marker.setLatLng([b.lat, b.lng])
      if (item.meta !== meta) {
        item.marker.setStyle({
          color: meta.color,
          fillColor: meta.fill,
          radius: meta.radius,
          fillOpacity: meta.fillOpacity
        })
        item.marker.setTooltipContent(`${b.bikeId}（${meta.label}）`)
        item.meta = meta
      }
    } else {
      const marker = L.circleMarker([b.lat, b.lng], {
        renderer,
        radius: meta.radius,
        color: meta.color,
        fillColor: meta.fill,
        fillOpacity: meta.fillOpacity,
        weight: 1
      })
        .bindTooltip(`${b.bikeId}（${meta.label}）`)
        .addTo(group)
      markers.set(b.bikeId, { marker, meta })
    }
  }
  for (const [id, item] of markers) {
    if (!seen.has(id)) {
      group.removeLayer(item.marker)
      item.marker.unbindTooltip()
      markers.delete(id)
    }
  }
}

watch(
  () => props.bikes,
  async bikes => {
    const map = await getMapAsync()
    if (disposed) {
      return
    }
    if (!group) {
      group = L.layerGroup().addTo(map)
    }
    if (!renderer) {
      renderer = L.canvas({ padding: 0.5 })
    }
    sync(bikes)
  },
  // 重新勾选图层时立即用当前数据渲染，不必等下一次轮询
  { immediate: true }
)

// 图层开关用 v-if 控制，卸载时必须移除图层与渲染器，否则取消勾选后画布仍留在地图上
onUnmounted(() => {
  disposed = true
  group?.remove()
  group = null
  markers.clear()
  renderer?.remove()
  renderer = null
})
</script>
