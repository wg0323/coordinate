<template><span></span></template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import L from 'leaflet'
import { getMapAsync } from '../../mapStore'
import { listFences } from '../../api/fence'
import type { FenceDto } from '../../types'
import type { FenceCellStatDto, FenceStatsDto } from '../../types/stats'

const props = defineProps<{ stats: FenceStatsDto | null }>()

/** 视野内最多渲染的围栏数（Canvas 上限，超出按名称序截断） */
const MAX_RENDER = 2000
/** 数量徽标最低缩放级别：再小围栏只有几像素宽，标签会糊成一片 */
const BADGE_MIN_ZOOM = 14
/** 单视野徽标上限：divIcon 是 DOM 节点，避免密集围栏堆积上千元素 */
const MAX_BADGES = 400

const fences = ref<FenceDto[]>([])
const statsRef = ref<FenceStatsDto | null>(null)

// 状态 → 填充/描边色（空置灰弱化，未设容量半透明白）
const STATUS_COLOR: Record<string, { color: string; label: string; fillOpacity: number }> = {
  normal: { color: '#00ff88', label: '正常', fillOpacity: 0.12 },
  nearFull: { color: '#ffdd59', label: '接近满载', fillOpacity: 0.2 },
  overflow: { color: '#ff4757', label: '已溢出', fillOpacity: 0.3 },
  empty: { color: '#4a586c', label: '空置', fillOpacity: 0.06 },
  noCapacity: { color: 'rgba(220,230,240,0.5)', label: '未设容量', fillOpacity: 0.05 }
}

function statusOf(cell: FenceCellStatDto | undefined): string {
  if (!cell) {
    return 'noCapacity'
  }
  if (cell.capacity === null || cell.capacity === 0) {
    return 'noCapacity'
  }
  if (cell.count === 0) {
    return 'empty'
  }
  const rate = cell.count / cell.capacity
  if (rate > 1.0) {
    return 'overflow'
  }
  if (rate >= 0.8) {
    return 'nearFull'
  }
  return 'normal'
}

let group: L.LayerGroup | null = null
let map: L.Map | null = null
/** Canvas 渲染器复用：每次 render 新建会在地图上堆积 canvas，长时间运行内存持续增长 */
let renderer: L.Canvas | null = null
let disposed = false

async function render() {
  if (!map) {
    map = await getMapAsync()
  }
  if (disposed || !map) {
    return
  }
  const currentMap = map
  if (group) {
    map.removeLayer(group)
    group = null
  }
  if (fences.value.length === 0) {
    return
  }
  const b = map.getBounds()
  const zoom = map.getZoom()
  group = L.layerGroup()
  if (!renderer) {
    // 围栏画在车辆点下方（overlayPane 默认 zIndex 400），填充不压住绿点
    if (!map.getPane('fencePane')) {
      const pane = map.createPane('fencePane')
      pane.style.zIndex = '350'
    }
    renderer = L.canvas({ padding: 0.5, pane: 'fencePane' })
  }
  let drawn = 0
  const badges: { latlng: L.LatLng; text: string; color: string; count: number }[] = []
  for (const f of fences.value) {
    if (drawn >= MAX_RENDER) {
      break
    }
    const ring = f.coordinates
    if (!ring || ring.length < 3) {
      continue
    }
    const latlngs = ring.map(([lng, lat]) => [lat, lng] as [number, number])
    // 视野外包框粗筛
    const bounds = L.latLngBounds(latlngs)
    if (!b.intersects(bounds) && !b.contains(bounds)) {
      continue
    }
    const cell = statsRef.value?.cells[String(f.fenceId)]
    const status = statusOf(cell)
    const meta = STATUS_COLOR[status]
    L.polygon(latlngs, {
      renderer,
      color: meta.color,
      weight: status === 'overflow' ? 2 : 1,
      fill: true,
      fillColor: meta.color,
      fillOpacity: meta.fillOpacity
    })
      .bindTooltip(
        () => {
          const c = statsRef.value?.cells[String(f.fenceId)]
          const cap = c && c.capacity ? `容量 ${c.capacity}｜当前停放 ${c.count} 辆｜装载率 ${(Math.min(c.loadRate, 9.99) * 100).toFixed(0)}%` : `当前停放 ${c?.count ?? 0} 辆｜未设容量`
          return `${f.name}<br/>${cap}`
        },
        { sticky: true }
      )
      .on('click', () => {
        const c = statsRef.value?.cells[String(f.fenceId)]
        const meta2 = STATUS_COLOR[statusOf(c)]
        const cap = c && c.capacity ? `容量 ${c.capacity}<br/>当前停放 ${c.count} 辆<br/>装载率 ${(Math.min(c.loadRate, 9.99) * 100).toFixed(0)}%` : `当前停放 ${c?.count ?? 0} 辆<br/>未设容量`
        L.popup({ closeButton: true, autoPan: false })
          .setLatLng(bounds.getCenter())
          .setContent(`<b>${f.name}</b><br/>${cap}<br/>状态：${meta2.label}`)
          .openOn(currentMap)
      })
      .addTo(group)
    // 数量徽标：仅停放数 > 0 且放大到足够级别时展示
    if (zoom >= BADGE_MIN_ZOOM && cell && cell.count > 0) {
      badges.push({
        latlng: bounds.getCenter(),
        text: cell.capacity ? `${cell.count}/${cell.capacity}` : String(cell.count),
        color: meta.color,
        count: cell.count
      })
    }
    drawn++
  }
  // 徽标超过上限时优先保留停放数多的围栏
  badges.sort((x, y) => y.count - x.count)
  for (const badge of badges.slice(0, MAX_BADGES)) {
    L.marker(badge.latlng, {
      icon: L.divIcon({
        className: 'fence-badge-anchor',
        html: `<span class="fence-badge" style="border-color:${badge.color};color:${badge.color}">${badge.text}</span>`,
        iconSize: [0, 0],
        iconAnchor: [0, 0]
      }),
      interactive: false,
      keyboard: false
    }).addTo(group)
  }
  group.addTo(map)
}

onMounted(async () => {
  try {
    fences.value = await listFences()
  } catch {
    /* 围栏列表拉取失败则不渲染该层 */
  }
  map = await getMapAsync()
  if (disposed) {
    return
  }
  map.on('moveend', render)
  await render()
})

// 图层开关用 v-if 控制，卸载时必须移除图层并解绑 moveend，否则取消勾选后仍会重绘
onUnmounted(() => {
  disposed = true
  group?.remove()
  group = null
  renderer?.remove()
  renderer = null
  map?.off('moveend', render)
  map = null
})

watch(
  () => props.stats,
  s => {
    if (s) {
      statsRef.value = s
      render()
    }
  }
)
</script>

<style>
/* divIcon 挂在 markerPane 上，scoped 样式作用不到；单类名保持全局唯一 */
.fence-badge-anchor {
  width: 0 !important;
  height: 0 !important;
  background: none;
  border: none;
}
.fence-badge {
  position: absolute;
  left: 0;
  top: 0;
  /* 底边抬到围栏线上方 4px，避免数字压住细长围栏 */
  transform: translate(-50%, calc(-100% - 4px));
  display: inline-block;
  padding: 0 3px;
  border: 1px solid;
  border-radius: 3px;
  background: rgba(10, 22, 40, 0.8);
  font: 10px/12px 'Roboto Mono', 'Consolas', monospace;
  white-space: nowrap;
  pointer-events: none;
  /* 抵消地图容器的深色滤镜，保持深底彩字（否则被反色成白底） */
  filter: invert(1) hue-rotate(180deg);
}
</style>
