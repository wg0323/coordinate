<template>
  <div class="panel kpi">
    <div class="panel-title">核心运营指标</div>
    <div class="rows">
      <div class="row" v-for="r in rows" :key="r.label">
        <span class="label">{{ r.label }}</span>
        <span class="num value" :class="{ 'num-change': flash }">{{ r.value }}</span>
      </div>
    </div>
    <div ref="ringEl" class="ring"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useChart } from '../../composables/useChart'
import type { StatsDto } from '../../types/stats'
import { fmtNum, fmtPct } from '../../utils/format'

const props = defineProps<{ stats: StatsDto | null }>()

const ringEl = ref<HTMLElement>()
const flash = ref(false)
const { ensure } = useChart(ringEl)

const rows = computed(() => {
  const s = props.stats
  return [
    { label: '总车辆数', value: fmtNum(s?.total) },
    { label: '在线车辆', value: fmtNum(s?.online) },
    { label: '骑行中', value: fmtNum(s?.riding) },
    { label: '空闲可租', value: fmtNum(s?.idle) },
    { label: '故障待修', value: '0' },
    { label: '离线车辆', value: fmtNum(s?.offline) }
  ]
})

function renderRing(rate: number) {
  const chart = ensure()
  if (!chart) {
    return
  }
  chart.setOption({
    series: [
      {
        type: 'pie',
        radius: ['62%', '80%'],
        label: {
          position: 'center',
          formatter: `在线率\n${fmtPct(rate)}`,
          color: '#e6f1ff',
          fontSize: 13,
          lineHeight: 18
        },
        data: [
          { value: rate * 100, itemStyle: { color: '#00ff88' } },
          { value: Math.max(0, 100 - rate * 100), itemStyle: { color: 'rgba(0,212,255,0.12)' } }
        ]
      }
    ]
  })
}

watch(
  () => props.stats,
  s => {
    if (s) {
      renderRing(s.onlineRate)
      flash.value = true
      setTimeout(() => (flash.value = false), 600)
    }
  }
)
</script>

<style scoped>
.kpi {
  display: flex;
  flex-direction: column;
}
.rows {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
}
.row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-dim);
}
.value {
  font-size: 15px;
}
.ring {
  height: 110px;
}
</style>
