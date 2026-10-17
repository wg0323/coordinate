<template><span></span></template>

<script setup lang="ts">
import { watch } from 'vue'
import L from 'leaflet'
import { getMap } from '../mapStore'
import type { HeatCellDto } from '../types'

const props = defineProps<{ cells: HeatCellDto[] }>()
const enabled = defineModel<boolean>('enabled', { default: true })

// Canvas 渲染器：千级六边形时比 SVG DOM 性能友好
let renderer: L.Canvas | null = null
let group: L.LayerGroup | null = null

// count 占比 → 颜色（绿 → 黄 → 红）
function colorFor(ratio: number): string {
  return `hsl(${120 - ratio * 120}, 80%, 50%)`
}

watch(
  () => props.cells,
  cells => {
    const map = getMap()
    if (group) {
      map.removeLayer(group)
      group = null
    }
    if (!enabled.value || cells.length === 0) {
      return
    }
    if (!renderer) {
      renderer = L.canvas({ padding: 0.5 })
    }
    group = L.layerGroup()
    const maxCount = Math.max(...cells.map(c => c.count), 1)
    for (const c of cells) {
      const ratio = c.count / maxCount
      // boundary 为 [lng, lat] 数组，直接还原真实 H3 六边形边界
      L.polygon(
        c.boundary.map(([lng, lat]) => [lat, lng] as [number, number]),
        {
          renderer,
          color: colorFor(ratio),
          weight: 1,
          fillColor: colorFor(ratio),
          fillOpacity: 0.55
        }
      )
        .bindTooltip(`${c.count} 辆 / 1min`)
        .addTo(group)
    }
    group.addTo(map)
  },
  { deep: false }
)
</script>
