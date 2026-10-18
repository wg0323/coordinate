<template>
  <div class="panel">
    <div class="panel-title">车辆状态分布</div>
    <div ref="roseEl" class="rose"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useChart } from '../../composables/useChart'
import type { StatsDto } from '../../types/stats'

const props = defineProps<{ stats: StatsDto | null }>()

const roseEl = ref<HTMLElement>()
const { ensure } = useChart(roseEl)

// 三种状态用高对比色，且标签/引导线同色，便于一眼对应
const STATUS_META = [
  { key: 'riding', name: '骑行中', color: '#00d4ff' },
  { key: 'idle', name: '空闲', color: '#00ff88' },
  { key: 'offline', name: '离线', color: '#8fa3b8' }
] as const

function render(s: StatsDto) {
  const chart = ensure()
  if (!chart) {
    return
  }
  const total = s.riding + s.idle + s.offline
  // 面板高度有限，外侧标签易被裁：环形图靠左 + 右侧彩色彩块图例（带百分比）
  const items = STATUS_META.map(m => ({
    name: m.name,
    value: total > 0 ? s[m.key] : 1,
    itemStyle: { color: m.color, opacity: total > 0 ? 1 : 0.15 }
  }))
  chart.setOption({
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'middle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 8,
      textStyle: { color: '#c7d6e8', fontSize: 11 },
      formatter: (name: string) => {
        const it = items.find(i => i.name === name)
        const pct = it && total > 0 ? ((it.value / total) * 100).toFixed(2) : '0.00'
        return `${name} ${pct}%`
      }
    },
    series: [
      {
        type: 'pie',
        // 环形图而非玫瑰图：骑行中/空闲占比小，等半径才能看清色带
        radius: ['34%', '66%'],
        center: ['30%', '50%'],
        minAngle: 8,
        itemStyle: { borderColor: '#0a1628', borderWidth: 1 },
        label: { show: false },
        labelLine: { show: false },
        data: items
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
.rose {
  flex: 1;
  min-height: 0;
}
.panel {
  display: flex;
  flex-direction: column;
}
</style>
