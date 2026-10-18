<template><span></span></template>

<script setup lang="ts">
import { watch } from 'vue'
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

let group: L.LayerGroup | null = null

watch(
  () => props.bikes,
  async bikes => {
    const map = await getMapAsync()
    if (group) {
      map.removeLayer(group)
    }
    group = L.layerGroup()
    // 全量点可达数千级：Canvas 渲染（SVG 逐个 DOM 会卡）
    const renderer = L.canvas({ padding: 0.5 })
    for (const b of bikes) {
      const meta = STATUS_COLOR[b.status ?? 'unknown'] ?? STATUS_COLOR.unknown
      L.circleMarker([b.lat, b.lng], {
        renderer,
        radius: 5,
        color: meta.color,
        fillColor: meta.fill,
        fillOpacity: 0.9,
        weight: 1
      })
        .bindTooltip(`${b.bikeId}（${meta.label}）`)
        .addTo(group)
    }
    group.addTo(map)
  }
)
</script>
