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

const STATUS_META = [
  { key: 'riding', name: '骑行中', color: '#00d4ff' },
  { key: 'idle', name: '空闲', color: '#00ff88' },
  { key: 'offline', name: '离线', color: '#5a6b82' }
] as const

function render(s: StatsDto) {
  const chart = ensure()
  if (!chart) {
    return
  }
  const total = s.riding + s.idle + s.offline
  chart.setOption({
    series: [
      {
        type: 'pie',
        roseType: 'area',
        radius: ['18%', '78%'],
        center: ['50%', '52%'],
        itemStyle: { borderColor: '#0a1628', borderWidth: 1 },
        label: {
          color: '#e6f1ff',
          fontSize: 12,
          formatter: (p: { name: string; percent: number }) => `${p.name} ${p.percent}%`
        },
        data: STATUS_META.map(m => ({
          name: m.name,
          value: total > 0 ? s[m.key] : 1,
          itemStyle: { color: m.color, opacity: total > 0 ? 1 : 0.15 }
        }))
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
