<template>
  <div class="map-area">
    <MapView dark />
    <HeatLayer v-model:enabled="heatAuto" :cells="heatCells" />
    <BikeLayer v-if="bikesVisible" :bikes="bikes" />
    <FenceStatusLayer v-if="toggles.fences" :stats="fenceStats" />
    <OpsLayer v-if="toggles.ops" />

    <!-- 图层开关控制条 -->
    <div class="panel layer-ctrl">
      <label v-for="t in TOGGLES" :key="t.key">
        <input type="checkbox" v-model="toggles[t.key as keyof typeof toggles]" />
        {{ t.label }}
      </label>
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import MapView from '../MapView.vue'
import HeatLayer from '../HeatLayer.vue'
import BikeLayer from './BikeLayer.vue'
import OpsLayer from './OpsLayer.vue'
import FenceStatusLayer from './FenceStatusLayer.vue'
import { fetchBBox } from '../../api/nearby'
import { fetchHeat } from '../../api/heat'
import { getMapAsync } from '../../mapStore'
import { usePolling } from '../../composables/usePolling'
import { fmtNum } from '../../utils/format'
import { TODAY_ORDERS } from '../../mock/dashboard'
import type { NearbyBike, HeatCellDto } from '../../types'
import type { FenceStatsDto, StatsDto } from '../../types/stats'

const props = defineProps<{ stats: StatsDto | null; fenceStats: FenceStatsDto | null }>()

const TOGGLES = [
  { key: 'bikes', label: '车辆分布' },
  { key: 'fences', label: '电子围栏' },
  { key: 'heat', label: '热力聚合' },
  { key: 'ops', label: '运维车辆' }
] as const

const toggles = ref<{ bikes: boolean; fences: boolean; heat: boolean; ops: boolean }>({
  bikes: true,
  fences: true,
  heat: false,
  ops: false
})

const bikes = ref<NearbyBike[]>([])
const heatCells = ref<HeatCellDto[]>([])
const zoom = ref(12)

// zoom 联动：<11 时热力蜂窝即聚合视图，散点隐藏
const bikesVisible = computed(() => toggles.value.bikes && zoom.value >= 11)
const heatAuto = computed(() => toggles.value.heat || zoom.value < 11)

async function pollBikes() {
  try {
    const map = await getMapAsync()
    const b = map.getBounds()
    bikes.value = await fetchBBox(b.getWest(), b.getSouth(), b.getEast(), b.getNorth())
  } catch {
    /* 保留上次数据 */
  }
}

async function pollHeat() {
  try {
    heatCells.value = await fetchHeat()
  } catch {
    /* 保留上次数据 */
  }
}

usePolling(pollBikes, 15_000)
usePolling(pollHeat, 30_000)

onMounted(async () => {
  const map = await getMapAsync()
  zoom.value = map.getZoom()
  map.on('zoomend', () => (zoom.value = map.getZoom()))
  map.on('moveend', pollBikes)
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
