<template>
  <div class="trace-layer"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import L from 'leaflet'
import { getMapAsync } from '../../mapStore'
import type { TracePoint } from '../../types'
import { TRACE_COLORS, colorOf, positionAt, smoothedLevels, splitSegments, traceBounds } from '../../utils/trace'

const props = defineProps<{ points: TracePoint[]; playbackTs?: number }>()

let map: L.Map | null = null
let group: L.LayerGroup | null = null
let renderer: L.Canvas | null = null
let playbackMarker: L.CircleMarker | null = null

function formatTime(ts: number) {
  return new Date(ts).toLocaleString('zh-CN', { hour12: false })
}

function draw() {
  if (!map || !group) return
  group.clearLayers()
  playbackMarker = null
  const points = props.points
  if (!points.length) return

  for (const run of splitSegments(points)) {
    if (run.kind === 'ride' && run.points.length > 1) {
      const levels = smoothedLevels(run.points)
      let start = 0
      for (let i = 1; i <= run.points.length; i++) {
        if (i === run.points.length || levels[i] !== levels[start]) {
          const seg = run.points.slice(start, Math.min(i + 1, run.points.length))
          if (seg.length > 1) {
            L.polyline(
              seg.map(p => [p.lat, p.lng] as L.LatLngTuple),
              { color: colorOf(levels[start]), weight: 4, opacity: 0.9, renderer: renderer ?? undefined }
            ).addTo(group)
          }
          start = i
        }
      }
    } else if (run.kind === 'park') {
      const first = run.points[0]
      const last = run.points[run.points.length - 1]
      L.circleMarker([first.lat, first.lng], {
        radius: 5,
        color: TRACE_COLORS.parked,
        fillColor: TRACE_COLORS.parked,
        fillOpacity: 0.9,
        renderer: renderer ?? undefined
      })
        .bindPopup(`停放 ${formatTime(first.ts)} – ${formatTime(last.ts)}<br/>约 ${Math.round((last.ts - first.ts) / 60000)} 分钟`)
        .addTo(group)
    }
  }

  const first = points[0]
  const last = points[points.length - 1]
  L.circleMarker([first.lat, first.lng], {
    radius: 7,
    color: TRACE_COLORS.mid,
    fillColor: TRACE_COLORS.mid,
    fillOpacity: 1,
    renderer: renderer ?? undefined
  })
    .bindPopup('起点')
    .addTo(group)
  L.circleMarker([last.lat, last.lng], {
    radius: 7,
    color: '#ff4757',
    fillColor: '#ff4757',
    fillOpacity: 1,
    renderer: renderer ?? undefined
  })
    .bindPopup('终点')
    .addTo(group)

  if (props.playbackTs !== undefined) {
    const pos = positionAt(points, props.playbackTs)
    playbackMarker = L.circleMarker([pos.lat, pos.lng], {
      radius: 8,
      color: '#00d4ff',
      fillColor: '#ffffff',
      fillOpacity: 1,
      weight: 3,
      renderer: renderer ?? undefined
    }).addTo(group)
  }

  const b = traceBounds(points)
  map.fitBounds(
    [
      [b.minLat, b.minLng],
      [b.maxLat, b.maxLng]
    ],
    { padding: [40, 40] }
  )
}

onMounted(async () => {
  map = await getMapAsync()
  renderer = L.canvas({ padding: 0.5 })
  group = L.layerGroup().addTo(map)
  draw()
})

watch(() => props.points, draw)

watch(
  () => props.playbackTs,
  ts => {
    if (!map || !playbackMarker || ts === undefined) return
    const pos = positionAt(props.points, ts)
    playbackMarker.setLatLng([pos.lat, pos.lng])
  }
)

onBeforeUnmount(() => {
  group?.clearLayers()
  group?.remove()
  renderer?.remove()
  group = null
  renderer = null
  playbackMarker = null
  map = null
})
</script>

<style scoped>
.trace-layer {
  display: none;
}
</style>
