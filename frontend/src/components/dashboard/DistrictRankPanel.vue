<template>
  <div class="panel rank-panel">
    <div class="panel-title">区域车辆数排行</div>
    <div ref="barEl" class="chart"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import echarts from '../../utils/echarts'
import type { EChartsType } from '../../utils/echarts'
import type { StatsDto } from '../../types/stats'

const props = defineProps<{ stats: StatsDto | null }>()

const barEl = ref<HTMLElement>()
let chart: EChartsType | null = null

function render(s: StatsDto) {
  if (!barEl.value) {
    return
  }
  if (!chart) {
    chart = echarts.init(barEl.value)
  }
  const top = s.districtCounts.slice(0, 12)
  chart.setOption({
    grid: { left: 46, right: 34, top: 6, bottom: 4 },
    xAxis: { type: 'value', show: false },
    yAxis: {
      type: 'category',
      data: top.map(d => d.name).reverse(),
      axisLabel: { color: '#7f9bbd', fontSize: 11 },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        type: 'bar',
        data: top.map(d => d.count).reverse(),
        barWidth: 10,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: 'rgba(0,212,255,0.25)' },
            { offset: 1, color: '#00d4ff' }
          ])
        },
        label: { show: true, position: 'right', color: '#e6f1ff', fontSize: 11 }
      }
    ]
  })
}

watch(
  () => props.stats,
  s => {
    if (s) {
      render(s)
    }
  }
)
</script>

<style scoped>
.rank-panel {
  display: flex;
  flex-direction: column;
}
.chart {
  flex: 1;
  min-height: 0;
}
</style>
