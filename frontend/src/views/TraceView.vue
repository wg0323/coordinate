<template>
  <div class="trace-page">
    <header class="panel trace-top">
      <a class="back" href="#/">← 返回大屏</a>
      <span class="title">超长轨迹查询</span>
      <span class="sub">{{ bikeId }} · 一天多段行程</span>
    </header>

    <aside class="panel trace-side">
      <div class="panel-title">查询条件</div>
      <label class="field">设备 ID<input v-model.trim="bikeId" /></label>
      <label class="field">开始时间<input v-model="fromInput" type="datetime-local" /></label>
      <label class="field">结束时间<input v-model="toInput" type="datetime-local" /></label>
      <button class="query" :disabled="loading" @click="query">
        {{ loading ? '查询中…' : '查询' }}
      </button>
      <p v-if="error" class="error">{{ error }}</p>
      <p v-if="truncated" class="warn">结果已达 20000 点上限，请缩小时间范围</p>

      <template v-if="stats">
        <div class="panel-title stats-title">轨迹统计</div>
        <div class="stat"><span>总里程</span><b class="num">{{ stats.distanceKm.toFixed(1) }} km</b></div>
        <div class="stat"><span>骑行时长</span><b class="num">{{ durationText }}</b></div>
        <div class="stat"><span>点数（原始/抽稀）</span><b class="num">{{ result?.rawCount }} / {{ result?.simplifiedCount }}</b></div>
        <div class="stat"><span>平均速度</span><b class="num">{{ stats.avgKmh.toFixed(1) }} km/h</b></div>
        <div class="stat"><span>最高速度</span><b class="num">{{ stats.maxKmh.toFixed(1) }} km/h</b></div>
      </template>

      <div class="legend">
        <span><i :style="{ background: TRACE_COLORS.slow }"></i>慢 &lt;10 km/h</span>
        <span><i :style="{ background: TRACE_COLORS.mid }"></i>中 10–14 km/h</span>
        <span><i :style="{ background: TRACE_COLORS.fast }"></i>快 ≥14 km/h</span>
        <span><i :style="{ background: TRACE_COLORS.parked }"></i>停放点</span>
      </div>
    </aside>

    <main class="trace-map">
      <MapView dark />
      <TraceLayer v-if="result && result.raw.length" :points="result.raw" :playback-ts="playbackTs" />
      <div v-if="result && !result.raw.length" class="empty">该时间段无轨迹</div>
    </main>

    <footer class="panel trace-playback">
      <span class="placeholder">查询后显示回放条（下一步接入）</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import MapView from '../components/MapView.vue'
import TraceLayer from '../components/trace/TraceLayer.vue'
import { fetchTrace } from '../api/trace'
import { TRACE_COLORS, buildTraceStats } from '../utils/trace'
import type { TraceResult } from '../types'

const bikeId = ref('BMXC000123')
const fromInput = ref('')
const toInput = ref('')
const loading = ref(false)
const error = ref('')
const result = ref<TraceResult | null>(null)
const playbackTs = ref(0)

function formatDateTime(d: Date) {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

function initDefaultRange() {
  const start = new Date()
  start.setHours(0, 0, 0, 0)
  start.setDate(start.getDate() - 1)
  const end = new Date(start)
  end.setDate(end.getDate() + 1)
  fromInput.value = formatDateTime(start)
  toInput.value = formatDateTime(end)
}

const stats = computed(() => (result.value && result.value.raw.length ? buildTraceStats(result.value.raw) : null))
const truncated = computed(() => (result.value?.rawCount ?? 0) >= 20000)
const durationText = computed(() => {
  if (!stats.value) return '-'
  const minutes = Math.round(stats.value.ridingMinutes)
  return `${Math.floor(minutes / 60)} h ${minutes % 60} min`
})

async function query() {
  const from = new Date(fromInput.value).getTime()
  const to = new Date(toInput.value).getTime()
  if (!bikeId.value) {
    error.value = '请输入设备 ID'
    return
  }
  if (!(from < to)) {
    error.value = '开始时间必须早于结束时间'
    return
  }
  loading.value = true
  error.value = ''
  try {
    result.value = await fetchTrace(bikeId.value, from, to)
    playbackTs.value = result.value.raw[0]?.ts ?? 0
  } catch (e) {
    error.value = e instanceof Error ? e.message : '查询失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  initDefaultRange()
  query()
})
</script>

<style scoped>
.trace-page {
  height: 100%;
  box-sizing: border-box;
  display: grid;
  grid-template-areas:
    'top top'
    'side map'
    'play play';
  grid-template-rows: 64px minmax(0, 1fr) 72px;
  grid-template-columns: 320px 1fr;
  gap: 10px;
  padding: 10px;
  background: var(--bg-main);
  overflow: hidden;
}
.trace-top {
  grid-area: top;
  display: flex;
  align-items: center;
  gap: 16px;
}
.trace-top .back {
  color: var(--accent);
  text-decoration: none;
  font-size: 13px;
}
.trace-top .title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--text-main);
}
.trace-top .sub {
  font-size: 12px;
  color: var(--text-dim);
}
.trace-side {
  grid-area: side;
  overflow: auto;
}
.trace-side .field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--text-dim);
}
.trace-side input {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--panel-border);
  border-radius: 3px;
  color: var(--text-main);
  padding: 6px 8px;
  font-family: var(--font-num);
  font-size: 13px;
}
.query {
  width: 100%;
  padding: 8px;
  background: rgba(0, 212, 255, 0.15);
  border: 1px solid var(--accent);
  border-radius: 3px;
  color: var(--accent);
  font-size: 14px;
  cursor: pointer;
}
.query:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.error {
  color: var(--danger);
  font-size: 12px;
}
.warn {
  color: var(--warn);
  font-size: 12px;
}
.stats-title {
  margin-top: 16px;
}
.stat {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-dim);
  padding: 4px 0;
  border-bottom: 1px dashed rgba(127, 155, 189, 0.15);
}
.stat b {
  color: var(--text-main);
}
.legend {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-top: 16px;
  padding-top: 8px;
  border-top: 1px dashed rgba(127, 155, 189, 0.25);
  font-size: 12px;
  color: var(--text-dim);
}
.legend span {
  display: flex;
  align-items: center;
  gap: 8px;
}
.legend i {
  width: 14px;
  height: 4px;
  border-radius: 2px;
}
.trace-map {
  grid-area: map;
  position: relative;
  border-radius: 4px;
  overflow: hidden;
  background: #06101d;
}
.empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: var(--text-dim);
  font-size: 14px;
  background: var(--bg-panel);
  padding: 10px 18px;
  border-radius: 4px;
}
.trace-playback {
  grid-area: play;
  display: flex;
  align-items: center;
}
.placeholder {
  color: var(--text-dim);
  font-size: 13px;
}
</style>
