<template>
  <div class="panel dispatch-panel">
    <div class="panel-title">智能调度建议</div>
    <!-- 建议列表按可用高度均布，底部状态行固定可见；不出现滚动条 -->
    <div class="advice-list">
      <div class="advice" v-for="(a, i) in DISPATCH_ADVICE" :key="i">
        <span class="a-route">📍 {{ a.from }} → {{ a.to }}</span>
        <span class="a-count">需调 <span class="num">{{ a.count }}</span> 辆</span>
        <span class="a-revenue">预计收益 <span class="num">¥{{ a.revenue.toLocaleString() }}</span></span>
      </div>
    </div>
    <div class="status-row">
      <span class="tag done">已执行 {{ DISPATCH_EXECUTED }} 条</span>
      <span class="tag pending">待执行 {{ DISPATCH_PENDING }} 条</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { DISPATCH_ADVICE, DISPATCH_EXECUTED, DISPATCH_PENDING } from '../../mock/dashboard'
</script>

<style scoped>
.dispatch-panel {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.advice-list {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
}
.advice {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: var(--text-dim);
  padding: 2px 0;
  border-bottom: 1px dashed rgba(127, 155, 189, 0.15);
}
.a-route {
  color: var(--text-main);
}
.a-count .num,
.a-revenue .num {
  color: var(--accent);
}
.status-row {
  display: flex;
  gap: 8px;
  flex: none;
}
.tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 2px;
}
.tag.done {
  color: var(--ok);
  border: 1px solid rgba(0, 255, 136, 0.4);
}
.tag.pending {
  color: var(--info);
  border: 1px solid rgba(255, 221, 89, 0.4);
}
</style>
