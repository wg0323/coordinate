<template>
  <div class="screen">
    <header class="area-top">
      <div class="panel topbar-placeholder">顶部标题栏</div>
    </header>
    <aside class="area-left">
      <KpiPanel :stats="stats" />
      <BikeStatusPanel :stats="stats" />
      <div class="panel ph">围栏状态概览</div>
      <div class="panel ph">实时订单流</div>
    </aside>
    <main class="area-center">
      <div class="panel ph map-placeholder">中央地图区域</div>
    </main>
    <aside class="area-right">
      <div class="panel ph">实时告警</div>
      <div class="panel ph">区域骑行量排行</div>
      <div class="panel ph">今日骑行趋势</div>
      <div class="panel ph">智能调度建议</div>
    </aside>
    <footer class="area-bottom">
      <div class="panel bottombar-placeholder">底部数据带</div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import KpiPanel from './components/dashboard/KpiPanel.vue'
import BikeStatusPanel from './components/dashboard/BikeStatusPanel.vue'
import { fetchStats } from './api/stats'
import { usePolling } from './composables/usePolling'
import type { StatsDto } from './types/stats'

// 大屏布局壳：grid 区域命名 top/left/center/right/bottom，各面板由子组件对号入座
// stats 统一轮询（10s），所有消费面板吃同一份数据
const stats = ref<StatsDto | null>(null)

async function pollStats() {
  try {
    stats.value = await fetchStats()
  } catch {
    /* 保留上次数据 */
  }
}

usePolling(pollStats, 10_000)
</script>

<style scoped>
.screen {
  height: 100%;
  display: grid;
  grid-template:
    'top top top' 80px
    'left center right' 1fr
    'bottom bottom bottom' 60px
    / minmax(340px, 20%) 1fr minmax(340px, 20%);
  gap: 10px;
  padding: 10px;
  background: var(--bg-main);
}
.area-top {
  grid-area: top;
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
.topbar-placeholder,
.bottombar-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dim);
}
.map-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dim);
}
/* 左右面板平分；占位面板由后续 Task 替换 */
.area-left > *,
.area-right > * {
  flex: 1;
  min-height: 0;
}
.area-left .ph,
.area-right .ph {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dim);
}
</style>
