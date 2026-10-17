<template>
  <div class="layout">
    <aside class="side">
      <section>
        <h3>附近车辆（题 5/6）</h3>
        <div class="row">
          <span>{{ bikes.length }} 辆在视野内（15s 轮询）</span>
        </div>
        <label class="row">
          <input v-model="heatEnabled" type="checkbox" /> 热力图（H3 聚合）
        </label>
        <label class="row">
          <input v-model="nearbyEnabled" type="checkbox" /> 车辆点位
        </label>
      </section>
      <TraceLayer @stats="s => (traceStats = s)" />
      <FenceLayer v-model:fences="fences" />
      <AlertLayer />
      <div v-if="traceStats" class="trace-stats">轨迹抽稀：{{ traceStats }} 点</div>
    </aside>
    <main class="main">
      <MapView />
      <HeatLayer :cells="heatCells" v-model:enabled="heatEnabled" />
      <NearbyLayer :bikes="nearbyEnabled ? bikes : []" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import MapView from './components/MapView.vue'
import HeatLayer from './components/HeatLayer.vue'
import NearbyLayer from './components/NearbyLayer.vue'
import TraceLayer from './components/TraceLayer.vue'
import FenceLayer from './components/FenceLayer.vue'
import AlertLayer from './components/AlertLayer.vue'
import { fetchBBox, fetchNearby } from './api/nearby'
import { fetchHeat } from './api/heat'
import { getMapAsync } from './mapStore'
import type { FenceDto, HeatCellDto, NearbyBike } from './types'

const bikes = ref<NearbyBike[]>([])
const heatCells = ref<HeatCellDto[]>([])
const fences = ref<FenceDto[]>([])
const heatEnabled = ref(false) // 默认关闭：六边形聚合层仅在演示 H3 聚合时开启
const nearbyEnabled = ref(true)
const traceStats = ref('')

let pollTimer: number | null = null
let heatTimer: number | null = null

// 车辆点位：视野范围查询（题 11 接口层：BoundingBox + zoom 语义）
async function pollBikes() {
  try {
    const b = (await getMapAsync()).getBounds()
    bikes.value = await fetchBBox(b.getWest(), b.getSouth(), b.getEast(), b.getNorth())
  } catch {
    /* 轮询容错 */
  }
}

async function pollHeat() {
  try {
    heatCells.value = await fetchHeat()
  } catch {
    /* 轮询容错 */
  }
}

onMounted(() => {
  pollBikes() // 内部等待地图就绪
  pollTimer = window.setInterval(pollBikes, 15000)
  heatTimer = window.setInterval(pollHeat, 30000)
  // 勾选开启时立即拉一次，不用等下一个 30s 周期
  watch(heatEnabled, on => {
    if (on) pollHeat()
  })
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (heatTimer) clearInterval(heatTimer)
})
</script>

<style>
* {
  box-sizing: border-box;
}
html,
body,
#app {
  height: 100%;
  margin: 0;
  font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;
}
.layout {
  display: flex;
  height: 100%;
}
.side {
  width: 320px;
  padding: 10px;
  overflow-y: auto;
  background: #fafafa;
  border-right: 1px solid #e0e0e0;
}
.side h3 {
  margin: 10px 0 6px;
  font-size: 14px;
}
.side section {
  border-bottom: 1px solid #eee;
  padding-bottom: 8px;
}
.row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 4px 0;
}
.main {
  flex: 1;
  position: relative;
}
.main .map-view {
  position: absolute;
  inset: 0;
}
.trace-stats {
  color: #e53935;
  font-size: 12px;
}
</style>
