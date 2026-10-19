<template>
  <div class="trace-page">
    <header class="panel trace-top">
      <a class="back" href="#/">← 返回大屏</a>
      <span class="title">超长轨迹查询</span>
      <span class="sub">固定设备 · 一天多段行程</span>
    </header>
    <aside class="panel trace-side">
      <div class="panel-title">查询条件</div>
      <p class="placeholder">查询面板将在后续任务实现</p>
    </aside>
    <main class="trace-map">
      <div class="placeholder">轨迹地图</div>
    </main>
    <footer class="panel trace-playback">
      <span class="placeholder">回放条</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
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
.trace-map {
  grid-area: map;
  position: relative;
  border-radius: 4px;
  overflow: hidden;
  background: #06101d;
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
