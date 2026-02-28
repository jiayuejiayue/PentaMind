<template>
  <div :class="layoutClass">
    <Sidebar />
    <div class="main-container">
      <Navbar />
      <AppMain />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/store/modules/app'
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'
import AppMain from './components/AppMain.vue'

const appStore = useAppStore()

const layoutClass = computed(() => ({
  'app-layout': true,
  'sidebar-opened': appStore.sidebar.opened,
  'sidebar-collapsed': !appStore.sidebar.opened,
}))
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  width: 100%;
  overflow: hidden;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: margin-left 0.28s ease;
}
</style>
