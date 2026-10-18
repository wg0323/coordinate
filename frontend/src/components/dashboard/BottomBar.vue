<template>
  <div class="panel bottombar">
    <div class="stats">
      <div class="stat" v-for="s in BOTTOM_STATS" :key="s.label">
        <span class="s-label">{{ s.label }}</span>
        <span class="num s-value">{{ s.value }}</span>
      </div>
    </div>
    <div class="ticker-wrap">
      <div class="ticker">
        <span v-for="(n, i) in items" :key="i">📢 {{ n }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BOTTOM_STATS, TICKER_NEWS } from '../../mock/dashboard'

// 跑马灯内容复制两份实现无缝滚动
const items = computed(() => [...TICKER_NEWS, ...TICKER_NEWS])
</script>

<style scoped>
.bottombar {
  display: flex;
  align-items: center;
  padding: 0 16px;
  overflow: hidden;
}
.stats {
  display: flex;
  gap: 34px;
  flex: none;
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.s-label {
  font-size: 11px;
  color: var(--text-dim);
}
.s-value {
  font-size: 16px;
  color: var(--accent);
}
.ticker-wrap {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  margin-left: 20px;
  mask-image: linear-gradient(to right, transparent, #000 8%, #000 92%, transparent);
}
.ticker {
  display: inline-flex;
  gap: 60px;
  white-space: nowrap;
  animation: ticker-scroll 30s linear infinite;
  font-size: 12px;
  color: var(--text-dim);
}
@keyframes ticker-scroll {
  0% {
    transform: translateX(0);
  }
  100% {
    transform: translateX(-50%);
  }
}
</style>
