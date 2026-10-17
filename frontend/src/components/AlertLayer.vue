<template>
  <div class="alert-panel">
    <h3>围栏告警（题 10，SSE 实时）</h3>
    <div class="conn">{{ connected ? '● 已连接' : '○ 未连接' }}</div>
    <ul>
      <li v-for="a in alerts.slice(0, 20)" :key="a.ts + a.bikeId" :class="a.event.toLowerCase()">
        <b>{{ a.event }}</b> {{ a.bikeId }} @ {{ a.fenceName }}
      </li>
    </ul>
    <div v-if="alerts.length === 0" class="hint">等待告警…（模拟器运行中围栏有车辆进出时触发）</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import L from 'leaflet'
import { getMapAsync } from '../mapStore'
import { subscribeAlerts } from '../api/alert'
import type { FenceAlert } from '../types'

const alerts = ref<FenceAlert[]>([])
const connected = ref(false)

onMounted(async () => {
  // AlertLayer 挂载早于 MapView，先等地图就绪再订阅 SSE
  const map = await getMapAsync()
  subscribeAlerts(a => {
    connected.value = true
    alerts.value.unshift(a)
    if (alerts.value.length > 100) {
      alerts.value.pop()
    }
    const color = a.event === 'ENTER' ? '#e53935' : '#43a047'
    L.circleMarker([a.lat, a.lng], { radius: 8, color, fillOpacity: 0.8 })
      .bindTooltip(`${a.event} ${a.bikeId} @ ${a.fenceName}`)
      .addTo(map)
  }).onerror = () => {
    connected.value = false
  }
})
</script>

<style scoped>
.alert-panel {
  font-size: 13px;
}
.conn {
  font-size: 12px;
  color: #2e7d32;
}
ul {
  list-style: none;
  padding: 0;
  margin: 4px 0;
  max-height: 200px;
  overflow-y: auto;
}
li.enter b {
  color: #e53935;
}
li.exit b {
  color: #43a047;
}
.hint {
  color: #999;
  font-size: 12px;
}
</style>
