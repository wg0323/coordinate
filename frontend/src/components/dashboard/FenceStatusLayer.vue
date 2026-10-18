<template><span></span></template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import L from 'leaflet'
import { getMap, getMapAsync } from '../../mapStore'
import { listFences } from '../../api/fence'
import type { FenceDto } from '../../types'
import type { FenceCellStatDto, FenceStatsDto } from '../../types/stats'

const props = defineProps<{ stats: FenceStatsDto | null }>()

/** 视野内最多渲染的围栏数（Canvas 上限，超出按名称序截断） */
const MAX_RENDER = 2000

const fences = ref<FenceDto[]>([])
const statsRef = ref<FenceStatsDto | null>(null)

// 状态 → 描边色（空置灰弱化，未设容量半透明白）
const STATUS_COLOR: Record<string, { color: string; label: string }> = {
  normal: { color: '#00ff88', label: '正常' },
  nearFull: { color: '#ffdd59', label: '接近满载' },
  overflow: { color: '#ff4757', label: '已溢出' },
  empty: { color: '#4a586c', label: '空置' },
  noCapacity: { color: 'rgba(220,230,240,0.5)', label: '未设容量' }
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
/** Canvas 渲染器复用：每次 render 新建会在地图上堆积 canvas，长时间运行内存持续增长 */
let renderer: L.Canvas | null = null
let disposed = false

async function render() {
  const map = await getMapAsync()
  if (disposed) {
    return
  }
  if (group) {
    map.removeLayer(group)
    group = null
  }
  if (fences.value.length === 0) {
    return
  }
  const b = map.getBounds()
  group = L.layerGroup()
  if (!renderer) {
    renderer = L.canvas({ padding: 0.5 })
  }
  let drawn = 0
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
      fill: false
    })
      .bindTooltip(
        () => {
          const c = statsRef.value?.cells[String(f.fenceId)]
          const cap = c && c.capacity ? `容量 ${c.capacity}｜当前 ${c.count} 辆｜装载率 ${(Math.min(c.loadRate, 9.99) * 100).toFixed(0)}%` : '未设容量'
          return `${f.name}<br/>${cap}`
        },
        { sticky: true }
      )
      .on('click', () => {
        const c = statsRef.value?.cells[String(f.fenceId)]
        const meta2 = STATUS_COLOR[statusOf(c)]
        const cap = c && c.capacity ? `容量 ${c.capacity}<br/>当前 ${c.count} 辆<br/>装载率 ${(Math.min(c.loadRate, 9.99) * 100).toFixed(0)}%` : '未设容量'
        L.popup({ closeButton: true, autoPan: false })
          .setLatLng(bounds.getCenter())
          .setContent(`<b>${f.name}</b><br/>${cap}<br/>状态：${meta2.label}`)
          .openOn(map)
      })
      .addTo(group)
    drawn++
  }
  group.addTo(map)
}

onMounted(async () => {
  try {
    fences.value = await listFences()
  } catch {
    /* 围栏列表拉取失败则不渲染该层 */
  }
  const map = await getMapAsync()
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
  getMap().off('moveend', render)
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
