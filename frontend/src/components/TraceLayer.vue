<template>
  <div class="trace-panel">
    <h3>轨迹回放（题 2）</h3>
    <div class="row">
      <input v-model="bikeId" placeholder="bikeId，如 BMXC002599" />
      <select v-model="windowMin">
        <option :value="5">近 5 分钟</option>
        <option :value="30">近 30 分钟</option>
        <option :value="120">近 2 小时</option>
      </select>
    </div>
    <div class="row">
      <label>抽稀阈值 {{ threshold }}</label>
      <input v-model.number="threshold" type="range" min="0" max="0.0005" step="0.00002" />
      <button @click="load">查询</button>
    </div>
    <div v-if="result" class="row stats">
      抽稀前 {{ result.rawCount }} 点 → 抽稀后 {{ result.simplifiedCount }} 点
      （JTS 对照 {{ result.jtsSimplifiedCount }}）
      <button :disabled="!result" @click="play">回放</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import L from 'leaflet'
import { getMap } from '../mapStore'
import { fetchTrace } from '../api/trace'
import type { TraceResponse } from '../types'

const emit = defineEmits<{ (e: 'stats', text: string): void }>()

const bikeId = ref('BMXC002599')
const windowMin = ref(30)
const threshold = ref(0.0001)
const result = ref<TraceResponse | null>(null)

let polyline: L.Polyline | null = null
let movingMarker: L.CircleMarker | null = null
let timer: number | null = null

async function load() {
  const to = Date.now()
  const from = to - windowMin.value * 60_000
  result.value = await fetchTrace(bikeId.value, from, to, threshold.value)
  const map = getMap()
  if (polyline) map.removeLayer(polyline)
  polyline = L.polyline(
    result.value.simplified.map(p => [p.lat, p.lng] as [number, number]),
    { color: '#e53935', weight: 3 }
  ).addTo(map)
  if (result.value.simplified.length > 0) {
    map.fitBounds(polyline.getBounds())
  }
  emit('stats', `${result.value.rawCount} → ${result.value.simplifiedCount}`)
}

function play() {
  const map = getMap()
  const points = result.value?.simplified ?? []
  if (points.length === 0) {
    return
  }
  if (movingMarker) map.removeLayer(movingMarker)
  if (timer) clearInterval(timer)
  let i = 0
  movingMarker = L.circleMarker([points[0].lat, points[0].lng], {
    radius: 7,
    color: '#e53935',
    fillColor: '#ff8a80',
    fillOpacity: 1
  }).addTo(map)
  timer = window.setInterval(() => {
    i++
    if (i >= points.length) {
      clearInterval(timer!)
      timer = null
      return
    }
    movingMarker!.setLatLng([points[i].lat, points[i].lng])
  }, 120)
}
</script>

<style scoped>
.trace-panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.row input[type='text'],
.row input:not([type]) {
  flex: 1;
  min-width: 0;
}
.stats {
  font-size: 12px;
  color: #555;
}
</style>
