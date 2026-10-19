<template>
  <div class="map-area">
    <MapView dark />
    <HeatLayer v-model:enabled="heatAuto" :bikes="bikes" />
    <BikeLayer v-if="bikesVisible" :bikes="bikes" />
    <FenceStatusLayer v-if="toggles.fences" :stats="fenceStats" />
    <DensityGridLayer v-if="toggles.grid" :bikes="bikes" />
    <OpsLayer v-if="toggles.ops" />

    <!-- 图层开关控制条 -->
    <div class="panel layer-ctrl">
      <label v-for="t in TOGGLES" :key="t.key">
        <input type="checkbox" v-model="toggles[t.key as keyof typeof toggles]" />
        {{ t.label }}
      </label>
      <div class="legend">
        <span><i class="lg-dot" style="background: #a8b3c1"></i>围栏内停放</span>
        <span><i class="lg-dot" style="background: #ffa502"></i>围栏外停放</span>
        <span><i class="lg-dot" style="background: #00ff88"></i>骑行中</span>
      </div>
      <div v-if="heatAuto" class="heat-legend">
        <span class="hl-title">在线车辆密度</span>
        <i class="hl-bar" :style="{ background: HEAT_LEGEND_CSS }"></i>
        <span class="hl-scale"><em>稀疏</em><em>中等</em><em>稠密</em></span>
      </div>
    </div>

    <!-- 悬浮核心数字条 -->
    <div class="panel float-kpis">
      <div class="fk">
        <span class="fk-label">在线车辆</span>
        <span class="num fk-value">{{ fmtNum(stats?.online) }}</span>
      </div>
      <div class="fk">
        <span class="fk-label">骑行中</span>
        <span class="num fk-value">{{ fmtNum(stats?.riding) }}</span>
      </div>
      <div class="fk">
        <span class="fk-label">今日订单</span>
        <span class="num fk-value">{{ fmtNum(TODAY_ORDERS) }}</span>
      </div>
      <div class="fk">
        <span class="fk-label">围栏外停车</span>
        <span class="num fk-value">{{ fmtNum(fenceStats?.parkedOutside) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import L from 'leaflet'
import MapView from '../MapView.vue'
import HeatLayer from '../HeatLayer.vue'
import BikeLayer from './BikeLayer.vue'
import OpsLayer from './OpsLayer.vue'
import DensityGridLayer from './DensityGridLayer.vue'
import FenceStatusLayer from './FenceStatusLayer.vue'
import { fetchBBox } from '../../api/nearby'
import { getMapAsync } from '../../mapStore'
import { usePolling } from '../../composables/usePolling'
import { fmtNum } from '../../utils/format'
import { HEAT_LEGEND_CSS } from '../../utils/heat'
import { TODAY_ORDERS } from '../../mock/dashboard'
import type { NearbyBike } from '../../types'
import type { FenceStatsDto, StatsDto } from '../../types/stats'

const props = defineProps<{ stats: StatsDto | null; fenceStats: FenceStatsDto | null }>()

const TOGGLES = [
  { key: 'bikes', label: '车辆分布' },
  { key: 'fences', label: '电子围栏' },
  { key: 'heat', label: '车辆热力' },
  { key: 'grid', label: '密度网格' },
  { key: 'ops', label: '运维车辆' }
] as const

const toggles = ref<{ bikes: boolean; fences: boolean; heat: boolean; grid: boolean; ops: boolean }>({
  bikes: true,
  fences: true,
  heat: true,
  grid: true,
  ops: false
})

const bikes = ref<NearbyBike[]>([])
const zoom = ref(12)
const maxZoom = ref(Infinity)
let map: L.Map | null = null

// zoom 联动：<11 时热力云即聚合视图，散点隐藏；网格只是悬停查询层，不占视觉，可与散点/热力并存
// 到最大缩放后热力云关闭，只保留散点和围栏
const atMaxZoom = computed(() => zoom.value >= maxZoom.value)
const heatAuto = computed(() => !atMaxZoom.value && (toggles.value.heat || zoom.value < 11))
const bikesVisible = computed(() => toggles.value.bikes && zoom.value >= 11 && !heatAuto.value)

async function pollBikes() {
  try {
    const map = await getMapAsync()
    const b = map.getBounds()
    bikes.value = await fetchBBox(b.getWest(), b.getSouth(), b.getEast(), b.getNorth())
  } catch {
    /* 保留上次数据 */
  }
}

// 拖动/缩放停止 400ms 后再拉点位，避免连续 moveend 触发请求风暴（旧请求未返回又发新请求）
let moveTimer: number | null = null
function onMoveEnd() {
  if (moveTimer) {
    clearTimeout(moveTimer)
  }
  moveTimer = window.setTimeout(pollBikes, 400)
}
const onZoomEnd = () => {
  if (map) {
    zoom.value = map.getZoom()
  }
}

usePolling(pollBikes, 15_000)

onMounted(async () => {
  map = await getMapAsync()
  zoom.value = map.getZoom()
  maxZoom.value = map.getMaxZoom()
  map.on('zoomend', onZoomEnd)
  map.on('moveend', onMoveEnd)
})

onUnmounted(() => {
  map?.off('zoomend', onZoomEnd)
  map?.off('moveend', onMoveEnd)
  map = null
  if (moveTimer) {
    clearTimeout(moveTimer)
  }
})
</script>

<style scoped>
.map-area {
  position: relative;
  height: 100%;
  border-radius: 4px;
  overflow: hidden;
}
.layer-ctrl {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  font-size: 12px;
  color: var(--text-dim);
}
.layer-ctrl label {
  cursor: pointer;
  user-select: none;
}
.legend {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px dashed rgba(127, 155, 189, 0.25);
  font-size: 11px;
}
.legend span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.heat-legend {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 128px;
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px dashed rgba(127, 155, 189, 0.25);
  font-size: 11px;
}
.hl-bar {
  width: 100%;
  height: 8px;
  border-radius: 2px;
}
.hl-scale {
  display: flex;
  justify-content: space-between;
  color: var(--text-dim);
}
.hl-scale em {
  font-style: normal;
}
.lg-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.float-kpis {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  display: flex;
  gap: 28px;
  padding: 8px 24px;
}
.fk {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.fk-label {
  font-size: 12px;
  color: var(--text-dim);
}
.fk-value {
  font-size: 22px;
  color: var(--accent);
}
</style>
