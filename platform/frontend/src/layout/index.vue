<template>
  <div :class="layoutClass">
    <Sidebar :width="sidebarWidth" />
    <!-- 拖拽手柄 -->
    <div
      v-if="appStore.sidebar.opened"
      class="resize-handle"
      @mousedown="startResize"
    ></div>
    <div class="main-container">
      <Navbar />
      <AppMain />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useAppStore } from '@/store/modules/app'
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'
import AppMain from './components/AppMain.vue'

const appStore = useAppStore()

// 侧边栏可拖拽宽度
const sidebarWidth = ref(210)
const isResizing = ref(false)

const layoutClass = computed(() => ({
  'app-layout': true,
  'sidebar-opened': appStore.sidebar.opened,
  'sidebar-collapsed': !appStore.sidebar.opened,
  'is-resizing': isResizing.value,
}))

function startResize(e) {
  isResizing.value = true
  const startX = e.clientX
  const startWidth = sidebarWidth.value

  function onMouseMove(e) {
    const delta = e.clientX - startX
    const newWidth = Math.min(400, Math.max(180, startWidth + delta))
    sidebarWidth.value = newWidth
  }

  function onMouseUp() {
    isResizing.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  width: 100%;
  overflow: hidden;
  position: relative;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.resize-handle {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  flex-shrink: 0;
  transition: background 0.2s;
  z-index: 10;
}
.resize-handle:hover,
.is-resizing .resize-handle {
  background: var(--pm-accent);
}
</style>
