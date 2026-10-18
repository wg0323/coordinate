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
    <div ref="barEl" class="bar"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useChart } from '../../composables/useChart'
import { fmtNum, fmtPct } from '../../utils/format'
import type { FenceStatsDto } from '../../types/stats'

const props = defineProps<{ fenceStats: FenceStatsDto | null }>()

const barEl = ref<HTMLElement>()
const { ensure } = useChart(barEl)

const COUNTS = [
  { key: 'normal', label: '正常', color: '#00ff88' },
  { key: 'nearFull', label: '接近满载', color: '#ffdd59' },
  { key: 'overflow', label: '已溢出', color: '#ff4757' },
  { key: 'empty', label: '空置', color: '#4a586c' },
  { key: 'noCapacity', label: '未设容量', color: '#8fa3b8' }
] as const

function renderBar(s: FenceStatsDto) {
  const chart = ensure()
  if (!chart) {
    return
  }
  const items = COUNTS.filter(c => c.key !== 'noCapacity')
  chart.setOption({
    grid: { left: 40, right: 10, top: 10, bottom: 22 },
    xAxis: { type: 'value', show: false },
    yAxis: {
      type: 'category',
      data: items.map(c => c.label),
      axisLabel: { color: '#7f9bbd', fontSize: 11 },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar',
        data: items.map(c => ({
          value: s[c.key],
          itemStyle: { color: c.color }
        })),
        barWidth: 12,
        label: { show: true, position: 'right', color: '#e6f1ff', fontSize: 11 }
      }
    ]
  })
}

watch(
  () => props.fenceStats,
  s => {
    if (s) {
      renderBar(s)
    }
  }
)
</script>

<style scoped>
.fence-overview {
  display: flex;
  flex-direction: column;
}
.counts {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.c-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-dim);
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
}
.avg-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-dim);
  margin-top: 6px;
}
.avg-value {
  color: var(--accent);
}
.bar {
  flex: 1;
  min-height: 0;
}
</style>
