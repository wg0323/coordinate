<template><span></span></template>

<script setup lang="ts">
import { onUnmounted, watch } from 'vue'
import L from 'leaflet'
import { getMapAsync } from '../../mapStore'
import type { NearbyBike } from '../../types'

const props = defineProps<{ bikes: NearbyBike[] }>()

/** 网格步长（度）：随缩放翻倍，保持格子屏幕宽度约 58px，能容下居中数字 */
const BASE_STEP = 0.02
const BASE_ZOOM = 12
/** 只有「当前视野最热的 10% 且不少于 3 辆」的格子才响应悬停 */
const MIN_COUNT = 3
const TOP_RATIO = 0.1

let map: L.Map | null = null
let tip: HTMLDivElement | null = null
let highlight: L.Rectangle | null = null
let hot = new Map<string, number>()
let step = BASE_STEP
let disposed = false
/** 最后一次悬停位置：数据刷新后据此重算，避免 15s 轮询把悬停数字清掉 */
let lastLatLng: L.LatLng | null = null

function stepFor(zoom: number): number {
  return BASE_STEP * Math.pow(2, BASE_ZOOM - zoom)
}

function keyOf(lng: number, lat: number): string {
  return `${Math.floor(lng / step)},${Math.floor(lat / step)}`
}

function boundsOf(lng: number, lat: number): L.LatLngBounds {
  const gx = Math.floor(lng / step) * step
  const gy = Math.floor(lat / step) * step
  return L.latLngBounds([gy, gx], [gy + step, gx + step])
}

function hideHover(): void {
  highlight?.remove()
  highlight = null
  if (tip) {
    tip.style.display = 'none'
  }
}

function resetHover(): void {
  lastLatLng = null
  hideHover()
}

/** 重新聚合当前视野，只保留热点格子（阈值 = P90 与 3 辆取高） */
function rebuild(): void {
  if (!map) {
    return
  }
  step = stepFor(map.getZoom())
  const counts = new Map<string, number>()
  for (const b of props.bikes) {
    const key = keyOf(b.lng, b.lat)
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  const sorted = [...counts.values()].sort((a, b) => a - b)
  const threshold = Math.max(MIN_COUNT, sorted[Math.floor(sorted.length * (1 - TOP_RATIO))] ?? 0)
  hot = new Map([...counts].filter(([, n]) => n >= threshold))
  if (lastLatLng) {
    showAt(lastLatLng)
  } else {
    hideHover()
  }
}

/** 悬停到热点格：高亮该格并在格中心显示车辆数；普通格不响应 */
function showAt(latlng: L.LatLng): void {
  if (!map || !tip) {
    return
  }
  const count = hot.get(keyOf(latlng.lng, latlng.lat))
  if (count === undefined) {
    hideHover()
    return
  }
  const bounds = boundsOf(latlng.lng, latlng.lat)
  if (!highlight) {
    highlight = L.rectangle(bounds, {
      color: 'rgba(0, 212, 255, 0.9)',
      weight: 1,
      fillColor: 'rgba(0, 212, 255, 0.12)',
      fillOpacity: 1,
      interactive: false
    }).addTo(map)
  } else {
    highlight.setBounds(bounds)
  }
  tip.textContent = `${count} 辆`
  // 数字固定在格子中心，而不是跟随鼠标
  const center = map.latLngToContainerPoint(bounds.getCenter())
  tip.style.left = `${center.x}px`
  tip.style.top = `${center.y}px`
  tip.style.display = 'block'
}

function onMouseMove(e: L.LeafletMouseEvent): void {
  lastLatLng = e.latlng
  showAt(e.latlng)
}

function onZoomEnd(): void {
  resetHover()
  rebuild()
}

watch(
  () => props.bikes,
  async () => {
    if (!map) {
      map = await getMapAsync()
      if (disposed) {
        return
      }
      tip = document.createElement('div')
      tip.className = 'density-hover-tip'
      map.getContainer().appendChild(tip)
      map.on('mousemove', onMouseMove)
      map.on('mouseout', resetHover)
      map.on('moveend', resetHover)
      map.on('zoomend', onZoomEnd)
    }
    rebuild()
  },
  { immediate: true }
)

onUnmounted(() => {
  disposed = true
  resetHover()
  map?.off('mousemove', onMouseMove)
  map?.off('mouseout', resetHover)
  map?.off('moveend', resetHover)
  map?.off('zoomend', onZoomEnd)
  tip?.remove()
  tip = null
})
</script>

<style>
/* 悬停浮层挂在 Leaflet 容器内，scoped 样式不生效 */
.density-hover-tip {
  position: absolute;
  z-index: 1000;
  display: none;
  padding: 2px 7px;
  border: 1px solid rgba(0, 212, 255, 0.5);
  border-radius: 3px;
  background: rgba(6, 18, 32, 0.85);
  color: #e6f1ff;
  font-family: var(--font-num);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  transform: translate(-50%, -50%);
  pointer-events: none;
}
</style>
