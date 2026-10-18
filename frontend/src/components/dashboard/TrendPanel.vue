<template>
  <div class="panel trend-panel">
    <div class="panel-title">今日骑行趋势</div>
    <div ref="lineEl" class="chart"></div>
    <div class="summary">
      <span>今日 <span class="num s-today">287,456</span></span>
      <span>昨日同期 <span class="num">265,890</span></span>
      <span class="up">↑ 8.1%</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import echarts from '../../utils/echarts'
import type { EChartsType } from '../../utils/echarts'
import { TREND_TODAY, TREND_YESTERDAY } from '../../mock/dashboard'

const lineEl = ref<HTMLElement>()
let chart: EChartsType | null = null

onMounted(() => {
  if (!lineEl.value) {
    return
  }
  chart = echarts.init(lineEl.value)
  chart.setOption({
    grid: { left: 34, right: 8, top: 8, bottom: 18 },
    xAxis: {
      type: 'category',
      data: Array.from({ length: 24 }, (_, i) => `${i}`),
      axisLabel: { color: '#7f9bbd', fontSize: 10, interval: 3 },
      axisLine: { lineStyle: { color: 'rgba(127,155,189,0.3)' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#7f9bbd', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(127,155,189,0.12)' } }
    },
    series: [
      {
        type: 'line',
        data: TREND_TODAY,
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#00ff88', width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(0,255,136,0.35)' },
            { offset: 1, color: 'rgba(0,255,136,0)' }
          ])
        }
      },
      {
        type: 'line',
        data: TREND_YESTERDAY,
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#7f9bbd', width: 1, type: 'dashed' }
      }
    ]
  })
})
</script>

<style scoped>
.trend-panel {
  display: flex;
  flex-direction: column;
}
.chart {
  flex: 1;
  min-height: 0;
}
.summary {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: var(--text-dim);
}
.s-today {
  color: var(--ok);
}
.up {
  color: var(--ok);
}
</style>
