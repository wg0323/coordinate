<template>
  <div class="panel order-feed">
    <div class="panel-title">实时订单流</div>
    <div class="feed">
      <TransitionGroup name="feed">
        <div class="feed-item" v-for="o in orders" :key="o.key">
          <span class="num o-time">{{ o.time }}</span>
          <span class="o-district">{{ o.district }}</span>
          <span class="o-action" :class="o.action === '开锁' ? 'unlock' : 'lock'">{{ o.action }}</span>
        </div>
      </TransitionGroup>
    </div>
    <div class="rate">每分钟订单 <span class="num">{{ ORDER_RATE_TEXT }}</span></div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ORDER_ACTIONS, ORDER_DISTRICTS, ORDER_RATE_TEXT } from '../../mock/dashboard'

interface FeedOrder {
  key: number
  time: string
  district: string
  action: '开锁' | '还车'
}

const orders = ref<FeedOrder[]>([])
let seq = 0
let timer: number | null = null

function nowLabel(): string {
  return new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

function pushOrder() {
  const order: FeedOrder = {
    key: seq++,
    time: nowLabel(),
    district: ORDER_DISTRICTS[seq % ORDER_DISTRICTS.length],
    action: ORDER_ACTIONS[seq % ORDER_ACTIONS.length]
  }
  orders.value.unshift(order)
  if (orders.value.length > 20) {
    orders.value.length = 20
  }
}

onMounted(() => {
  // 初始铺 8 条，随后每 2s 滚动
  for (let i = 0; i < 8; i++) {
    pushOrder()
  }
  timer = window.setInterval(pushOrder, 2000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<style scoped>
.order-feed {
  display: flex;
  flex-direction: column;
}
.feed {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.feed-item {
  display: flex;
  gap: 10px;
  font-size: 12px;
  line-height: 22px;
  color: var(--text-dim);
}
.o-time {
  color: var(--text-dim);
}
.o-district {
  flex: 1;
  color: var(--text-main);
}
.o-action {
  padding: 0 6px;
  border-radius: 2px;
  font-size: 11px;
}
.unlock {
  color: var(--ok);
  border: 1px solid rgba(0, 255, 136, 0.4);
}
.lock {
  color: var(--accent);
  border: 1px solid rgba(0, 212, 255, 0.4);
}
.rate {
  font-size: 12px;
  color: var(--text-dim);
  margin-top: 4px;
}
.rate .num {
  color: var(--accent);
}
.feed-enter-active {
  transition: all 0.4s ease;
}
.feed-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}
.feed-move-active {
  transition: transform 0.4s ease;
}
</style>
