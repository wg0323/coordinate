<template>
  <div class="panel topbar">
    <div class="brand">
      <span class="logo">🚲</span>
      <span class="title">北京城区共享单车实时监控平台</span>
    </div>
    <div class="right">
      <a class="trace-link" href="#/trace">轨迹查询</a>
      <span class="weather">{{ WEATHER_TEXT }}</span>
      <span class="num clock breath">{{ clock }}</span>
      <span class="sys-status">
        <span class="dot" :class="online ? 'ok' : 'bad'"></span>
        {{ online ? '系统正常' : '连接异常' }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { WEATHER_TEXT } from '../../mock/dashboard'
import type { StatsDto } from '../../types/stats'

// 系统状态点 = /api/stats 最近一次轮询成败（null=尚未成功）
const props = defineProps<{ stats: StatsDto | null }>()

const clock = ref('')
let timer: number | null = null

const online = computed(() => props.stats !== null)

function tick() {
  clock.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

onMounted(() => {
  tick()
  timer = window.setInterval(tick, 1000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.logo {
  font-size: 26px;
}
.title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--text-main);
  text-shadow: 0 0 14px rgba(0, 212, 255, 0.55);
}
.right {
  display: flex;
  align-items: center;
  gap: 24px;
}
.weather {
  font-size: 13px;
  color: var(--text-dim);
}
.clock {
  font-size: 18px;
  color: var(--accent);
}
.sys-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-dim);
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.dot.ok {
  background: var(--ok);
  box-shadow: 0 0 8px var(--ok);
}
.dot.bad {
  background: var(--danger);
  box-shadow: 0 0 8px var(--danger);
}
.trace-link {
  font-size: 13px;
  color: var(--accent);
  text-decoration: none;
  border: 1px solid var(--panel-border);
  border-radius: 3px;
  padding: 3px 10px;
}
.trace-link:hover {
  background: rgba(0, 212, 255, 0.12);
}
</style>
