<template>
  <div class="panel alert-panel">
    <div class="panel-title">
      实时告警
      <span class="badge" :class="{ 'pulse-dot': alerts.length > 0 }">{{ alerts.length }}</span>
    </div>
    <div v-if="disconnected" class="offline-bar">连接断开，自动重连中…</div>
    <div class="list">
      <div class="alert-item" v-for="a in alerts" :key="a.key">
        <span class="a-dot" :class="a.event === 'ENTER' ? 'enter' : 'exit'"></span>
        <span class="a-body">
          <span class="a-fence">{{ a.fenceName }}</span>
          <span class="a-meta">{{ a.bikeId }} · {{ a.action }} · {{ a.ago }}</span>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { listAlerts, subscribeAlerts } from '../../api/alert'
import type { FenceAlert } from '../../types'

interface AlertItem extends FenceAlert {
  key: string
  action: string
  ago: string
}

const MAX_ITEMS = 30

const alerts = ref<AlertItem[]>([])
const disconnected = ref(false)
let seq = 0
let es: EventSource | null = null

function toItem(a: FenceAlert): AlertItem {
  return {
    ...a,
    key: `${a.ts}-${seq++}`,
    action: a.event === 'ENTER' ? '驶入' : '驶出',
    ago: `${Math.max(0, Math.round((Date.now() - a.ts) / 60000))} 分钟前`
  }
}

function push(a: FenceAlert) {
  alerts.value.unshift(toItem(a))
  if (alerts.value.length > MAX_ITEMS) {
    alerts.value.length = MAX_ITEMS
  }
}

onMounted(async () => {
  // 首屏回填最近历史
  try {
    const history = await listAlerts()
    for (const raw of history.slice().reverse()) {
      push(raw as FenceAlert)
    }
  } catch {
    /* 历史不可达则等 SSE */
  }
  es = subscribeAlerts(push)
  // SSE 断线状态（浏览器原生自动重连）
  es.onopen = () => (disconnected.value = false)
  es.onerror = () => (disconnected.value = true)
})

onBeforeUnmount(() => {
  es?.close()
})
</script>

<style scoped>
.alert-panel {
  display: flex;
  flex-direction: column;
}
.badge {
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 8px;
  background: rgba(255, 71, 87, 0.2);
  color: var(--danger);
  font-size: 12px;
}
.offline-bar {
  font-size: 12px;
  color: var(--warn);
  background: rgba(255, 165, 2, 0.1);
  padding: 2px 8px;
  margin-bottom: 4px;
  border-radius: 2px;
}
.list {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.alert-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 3px 0;
  border-bottom: 1px dashed rgba(127, 155, 189, 0.15);
}
.a-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex: none;
}
.a-dot.enter {
  background: var(--danger);
  box-shadow: 0 0 6px var(--danger);
}
.a-dot.exit {
  background: var(--ok);
}
.a-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.a-fence {
  font-size: 12px;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.a-meta {
  font-size: 11px;
  color: var(--text-dim);
}
</style>
