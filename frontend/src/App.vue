<template>
  <div class="screen">
    <header class="area-top">
      <div class="panel topbar-placeholder">顶部标题栏</div>
    </header>
    <aside class="area-left">
      <div class="panel ph">核心指标</div>
      <div class="panel ph">车辆状态分布</div>
      <div class="panel ph">围栏状态概览</div>
      <div class="panel ph">实时订单流</div>
    </aside>
    <main class="area-center">
      <div class="panel ph map-placeholder">中央地图区域</div>
    </main>
    <aside class="area-right">
      <div class="panel ph">实时告警</div>
      <div class="panel ph">区域骑行量排行</div>
      <div class="panel ph">今日骑行趋势</div>
      <div class="panel ph">智能调度建议</div>
    </aside>
    <footer class="area-bottom">
      <div class="panel bottombar-placeholder">底部数据带</div>
    </footer>
  </div>
</template>

<script setup lang="ts">
// 大屏布局壳：grid 区域命名 top/left/center/right/bottom，各面板由子组件对号入座
</script>

<style scoped>
.screen {
  height: 100%;
  display: grid;
  grid-template:
    'top top top' 80px
    'left center right' 1fr
    'bottom bottom bottom' 60px
    / minmax(340px, 20%) 1fr minmax(340px, 20%);
  gap: 10px;
  padding: 10px;
  background: var(--bg-main);
}
.area-top {
  grid-area: top;
}
.area-left {
  grid-area: left;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.area-right {
  grid-area: right;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.area-center {
  grid-area: center;
  position: relative;
}
.area-bottom {
  grid-area: bottom;
}
.topbar-placeholder,
.bottombar-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dim);
}
.map-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-dim);
}
/* 左右面板占位平分，后续挂入真实组件后移除 */
.area-left .panel,
.area-right .panel {
  flex: 1;
}
</style>
