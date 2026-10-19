<template>
  <div class="panel fence-overview">
    <div class="panel-title">电子围栏状态 ({{ fmtNum(fenceStats?.total) }})</div>
    <div class="counts">
      <div class="c-row" v-for="c in COUNTS" :key="c.key">
        <span class="dot" :style="{ background: c.color }"></span>
        <span class="c-label">{{ c.label }}</span>
        <span class="num c-value">{{ fmtNum(fenceStats?.[c.key]) }}</span>
      </div>
    </div>
    <div class="avg-row">
      <span>平均装载率</span>
      <span class="num avg-value">{{ fmtPct(fenceStats?.avgLoadRate) }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { fmtNum, fmtPct } from '../../utils/format'
import type { FenceStatsDto } from '../../types/stats'

defineProps<{ fenceStats: FenceStatsDto | null }>()

const COUNTS = [
  { key: 'normal', label: '正常', color: '#00ff88' },
  { key: 'nearFull', label: '接近满载', color: '#ffdd59' },
  { key: 'overflow', label: '已溢出', color: '#ff4757' },
  { key: 'empty', label: '空置', color: '#4a586c' },
  { key: 'noCapacity', label: '未设容量', color: '#8fa3b8' }
] as const
</script>

<style scoped>
.fence-overview {
  display: flex;
  flex-direction: column;
}
.counts {
  display: flex;
  flex-direction: column;
  gap: 0;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  justify-content: space-around;
}
.c-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  line-height: 1.2;
  color: var(--text-dim);
  padding: 4px 0;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.c-label {
  flex: 1;
}
.c-value {
  color: var(--text-main);
  font-size: 14px;
}
.avg-row {
  display: flex;
  flex: none;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-dim);
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px dashed rgba(127, 155, 189, 0.15);
}
.avg-value {
  color: var(--accent);
  font-size: 14px;
}
</style>
