<template>
  <div class="screen">
    <header class="area-top">
      <TopBar :stats="stats" />
    </header>
    <aside class="area-left">
      <KpiPanel :stats="stats" />
      <BikeStatusPanel :stats="stats" />
      <FenceOverviewPanel :fence-stats="fenceStats" />
      <OrderFeedPanel />
    </aside>
    <main class="area-center">
      <MapArea :stats="stats" :fence-stats="fenceStats" />
    </main>
    <aside class="area-right">
      <AlertPanel />
      <DistrictRankPanel :stats="stats" />
      <TrendPanel />
      <DispatchPanel />
    </aside>
    <footer class="area-bottom">
      <BottomBar />
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import KpiPanel from '../components/dashboard/KpiPanel.vue'
import BikeStatusPanel from '../components/dashboard/BikeStatusPanel.vue'
import FenceOverviewPanel from '../components/dashboard/FenceOverviewPanel.vue'
import OrderFeedPanel from '../components/dashboard/OrderFeedPanel.vue'
import MapArea from '../components/dashboard/MapArea.vue'
import AlertPanel from '../components/dashboard/AlertPanel.vue'
import DistrictRankPanel from '../components/dashboard/DistrictRankPanel.vue'
import TrendPanel from '../components/dashboard/TrendPanel.vue'
import DispatchPanel from '../components/dashboard/DispatchPanel.vue'
import TopBar from '../components/dashboard/TopBar.vue'
import BottomBar from '../components/dashboard/BottomBar.vue'
import { fetchFenceStats, fetchStats } from '../api/stats'
import { usePolling } from '../composables/usePolling'
import type { FenceStatsDto, StatsDto } from '../types/stats'

// 大屏布局壳：grid 区域命名 top/left/center/right/bottom，各面板由子组件对号入座
// stats（10s）与 fenceStats（30s）统一轮询，所有消费面板吃同一份数据
const stats = ref<StatsDto | null>(null)
const fenceStats = ref<FenceStatsDto | null>(null)

async function pollStats() {
  try {
    stats.value = await fetchStats()
  } catch {
    /* 保留上次数据 */
  }
}

async function pollFenceStats() {
  try {
    fenceStats.value = await fetchFenceStats()
  } catch {
    /* 保留上次数据 */
  }
}

usePolling(pollStats, 10_000)
usePolling(pollFenceStats, 30_000)
</script>

<style scoped>
.screen {
  height: 100%;
  box-sizing: border-box;
  display: grid;
  grid-template-areas:
    'top top top'
    'left center right'
    'bottom bottom bottom';
  grid-template-rows: 80px minmax(0, 1fr) 60px;
  grid-template-columns: minmax(340px, 20%) 1fr minmax(340px, 20%);
  gap: 10px;
  padding: 10px;
  background: var(--bg-main);
  overflow: hidden;
}
.area-top {
  grid-area: top;
}
/* 顶部栏撑满 80px 行高，否则 panel 只按内容高度收缩，行内下方留空 */
.area-top > * {
  height: 100%;
  box-sizing: border-box;
}
.area-left {
  grid-area: left;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.area-right {
  grid-area: right;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.area-center {
  grid-area: center;
  position: relative;
}
.area-bottom {
  grid-area: bottom;
}
/* 左右面板：核心指标权重更高，其余平分；min-height 防内容撑破列 */
.area-left > *,
.area-right > * {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.area-left > :first-child {
  flex: 1.3;
}
</style>
