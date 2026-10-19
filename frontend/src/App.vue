<template>
  <DashboardView v-if="page === 'dashboard'" />
  <TraceView v-else />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import DashboardView from './views/DashboardView.vue'
import TraceView from './views/TraceView.vue'

// 仅两个页面，用 hash 切换，不引入 vue-router
function currentPage(): 'dashboard' | 'trace' {
  return location.hash.startsWith('#/trace') ? 'trace' : 'dashboard'
}

const page = ref(currentPage())
function onHashChange() {
  page.value = currentPage()
}

onMounted(() => window.addEventListener('hashchange', onHashChange))
onBeforeUnmount(() => window.removeEventListener('hashchange', onHashChange))
</script>
