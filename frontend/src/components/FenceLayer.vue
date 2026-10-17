<template>
  <div class="fence-panel">
    <h3>电子围栏（题 7/10）</h3>
    <p class="hint">用地图上的绘制工具画多边形 → 保存即落库 + 广播给 Flink</p>
    <div class="row stats">
      共 {{ fences.length }} 条围栏，视野内渲染 {{ renderedCount }} 条
    </div>
    <ul>
      <li v-for="f in fences.slice(0, 50)" :key="f.fenceId ?? 0">
        <span>{{ f.fenceId }} - {{ f.name }}</span>
        <button @click="remove(f.fenceId!)">删除</button>
      </li>
    </ul>
    <div v-if="fences.length > 50" class="hint">列表仅显示前 50 条…</div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet-draw'
import { getMap, getMapAsync } from '../mapStore'
import { createFence, deleteFence, listFences } from '../api/fence'
import type { FenceDto } from '../types'

const fences = defineModel<FenceDto[]>('fences', { default: () => [] })

// 上万围栏的渲染策略（知识点：前端渲染瓶颈）：
// 1. 真实围栏层用 Canvas 渲染器（SVG 逐个 DOM 会卡死）
// 2. 只渲染当前视野内（moveend 时按外包框粗筛重画）
// 3. 绘制控件单独用 drawGroup，不与海量围栏混管
const MAX_RENDER = 2000

let drawGroup: L.FeatureGroup | null = null
let fenceLayer: L.LayerGroup | null = null
let renderer: L.Canvas | null = null
const renderedCount = ref(0)

/** 外包框粗筛：任一顶点落在（外扩 10% 的）视野内即纳入 */
function inView(f: FenceDto, b: L.LatLngBounds): boolean {
  for (const [lng, lat] of f.coordinates) {
    if (b.contains([lat, lng])) return true
  }
  return false
}

function renderFences() {
  if (!fenceLayer) return
  const map = getMap()
  fenceLayer.clearLayers()
  const b = map.getBounds().pad(0.1)
  let count = 0
  for (const f of fences.value) {
    if (count >= MAX_RENDER) break
    if (!inView(f, b)) continue
    L.polygon(f.coordinates.map(c => [c[1], c[0]] as [number, number]), {
      renderer: renderer!,
      color: '#7b1fa2',
      weight: 1,
      fillColor: '#ce93d8',
      fillOpacity: 0.25
    })
      .bindTooltip(`${f.fenceId} - ${f.name}`)
      .addTo(fenceLayer)
    count++
  }
  renderedCount.value = count
}

function onViewChange() {
  renderFences()
}

onMounted(async () => {
  // FenceLayer 挂载早于 MapView，必须等地图就绪（否则 getMap 抛错中断整个围栏初始化）
  const map = await getMapAsync()
  drawGroup = new L.FeatureGroup()
  map.addLayer(drawGroup)
  renderer = L.canvas({ padding: 0.5 })
  fenceLayer = L.layerGroup()
  fenceLayer.addTo(map)

  // leaflet-draw 绘制控件：仅多边形
  const drawControl = new L.Control.Draw({
    position: 'topright',
    draw: {
      polyline: false,
      circle: false,
      rectangle: false,
      marker: false,
      circlemarker: false,
      polygon: { allowIntersection: false }
    },
    edit: { featureGroup: drawGroup }
  })
  map.addControl(drawControl)

  map.on(L.Draw.Event.CREATED, async (e: any) => {
    const layer = e.layer as L.Polygon
    const latlngs = layer.getLatLngs()[0] as L.LatLng[]
    const fence: FenceDto = {
      fenceId: null,
      name: `fence-${Date.now() % 100000}`,
      coordinates: latlngs.map(p => [p.lng, p.lat])
    }
    await createFence(fence)
    await reload()
    drawGroup!.addLayer(layer)
  })

  map.on('moveend', onViewChange)

  await reload()
})

onBeforeUnmount(() => {
  const map = getMap()
  map.off('moveend', onViewChange)
})

async function reload() {
  fences.value = await listFences()
  renderFences()
}

watch(
  () => fences.value,
  () => renderFences()
)

async function remove(fenceId: number) {
  await deleteFence(fenceId)
  await reload()
}
</script>

<style scoped>
.fence-panel {
  font-size: 13px;
}
.hint {
  color: #777;
  font-size: 12px;
}
.stats {
  font-size: 12px;
  color: #555;
}
ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 0;
}
</style>
