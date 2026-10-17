<template><span></span></template>

<script setup lang="ts">
import { watch } from 'vue'
import L from 'leaflet'
import { getMap } from '../mapStore'
import type { NearbyBike } from '../types'

const props = defineProps<{ bikes: NearbyBike[] }>()

let group: L.LayerGroup | null = null

watch(
  () => props.bikes,
  bikes => {
    const map = getMap()
    if (group) {
      map.removeLayer(group)
    }
    group = L.layerGroup()
    // 全量点可能达数千级：Canvas 渲染（SVG 逐个 DOM 会卡）
    const renderer = L.canvas({ padding: 0.5 })
    for (const b of bikes) {
      L.circleMarker([b.lat, b.lng], {
        renderer,
        radius: 5,
        color: '#1565c0',
        fillColor: '#42a5f5',
        fillOpacity: 0.9,
        weight: 1
      })
        .bindTooltip(`${b.bikeId} (${Math.round(b.distM)}m)`)
        .addTo(group)
    }
    group.addTo(map)
  }
)
</script>
