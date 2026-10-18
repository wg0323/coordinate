<template><span></span></template>

<script setup lang="ts">
import { onUnmounted, watch } from 'vue'
import L from 'leaflet'
import { getMapAsync } from '../../mapStore'
import type { NearbyBike } from '../../types'

const props = defineProps<{ bikes: NearbyBike[] }>()

// 状态着色：绿=空闲可租，蓝灰=骑行中，蓝=无状态数据
const STATUS_COLOR: Record<string, { color: string; fill: string; label: string }> = {
  idle: { color: '#00875a', fill: '#00ff88', label: '空闲' },
  riding: { color: '#77808c', fill: '#9aa5b1', label: '骑行中' },
  unknown: { color: '#0d47a1', fill: '#1565c0', label: '状态未知' }
}

interface BikeMarker {
  marker: L.CircleMarker
  status: string
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
    const status = b.status ?? 'unknown'
    const meta = STATUS_COLOR[status] ?? STATUS_COLOR.unknown
    const item = markers.get(b.bikeId)
    if (item) {
      item.marker.setLatLng([b.lat, b.lng])
      if (item.status !== status) {
        item.marker.setStyle({ color: meta.color, fillColor: meta.fill })
        item.marker.setTooltipContent(`${b.bikeId}（${meta.label}）`)
        item.status = status
      }
    } else {
      const marker = L.circleMarker([b.lat, b.lng], {
        renderer,
        radius: 5,
        color: meta.color,
        fillColor: meta.fill,
        fillOpacity: 0.9,
        weight: 1
      })
        .bindTooltip(`${b.bikeId}（${meta.label}）`)
        .addTo(group)
      markers.set(b.bikeId, { marker, status })
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
