<template>
  <div :class="['sidebar-container', { 'is-collapsed': !appStore.sidebar.opened }]" :style="containerStyle">
    <!-- Logo区域 -->
    <div class="sidebar-logo" @click="$router.push('/')">
      <span class="logo-icon">⬡</span>
      <transition name="fade">
        <span v-if="appStore.sidebar.opened" class="logo-title">PentaMind</span>
      </transition>
    </div>

    <!-- 菜单 -->
    <el-scrollbar>
      <el-menu
        :default-active="activeMenu"
        :collapse="!appStore.sidebar.opened"
        :collapse-transition="false"
        background-color="transparent"
        text-color="var(--pm-sidebar-text)"
        active-text-color="var(--pm-sidebar-text-active)"
        :unique-opened="true"
        router
      >
        <template v-for="route in menuRoutes" :key="route.path">
          <!-- 单级菜单 -->
          <el-menu-item
            v-if="!route.children || route.children.length === 1"
            :index="getSinglePath(route)"
          >
            <el-icon><component :is="route.meta?.icon || route.children?.[0]?.meta?.icon" /></el-icon>
            <template #title>{{ route.meta?.title || route.children?.[0]?.meta?.title }}</template>
          </el-menu-item>

          <!-- 多级子菜单 -->
          <el-sub-menu v-else :index="route.path">
            <template #title>
              <el-icon><component :is="route.meta?.icon" /></el-icon>
              <span>{{ route.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in route.children"
              :key="child.path"
              :index="resolvePath(route.path, child.path)"
            >
              <el-icon><component :is="child.meta?.icon" /></el-icon>
              <template #title>{{ child.meta?.title }}</template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/store/modules/app'

const props = defineProps({
  width: { type: Number, default: 210 },
})

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const containerStyle = computed(() => {
  if (!appStore.sidebar.opened) return { width: '64px' }
  return { width: props.width + 'px' }
})

const activeMenu = computed(() => route.path)

// 过滤掉 hidden 路由
const menuRoutes = computed(() =>
  router.options.routes.filter(r => !r.meta?.hidden)
)

function getSinglePath(route) {
  if (route.children?.length === 1) {
    return resolvePath(route.path, route.children[0].path)
  }
  return route.redirect || route.path
}

function resolvePath(parent, child) {
  if (child.startsWith('/')) return child
  return `${parent}/${child}`.replace(/\/+/g, '/')
}
</script>

<style scoped>
.sidebar-container {
  height: 100vh;
  background: var(--pm-bg-sidebar);
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--pm-border);
}

.sidebar-container.is-collapsed {
  transition: width 0.28s ease;
}

/* Logo */
.sidebar-logo {
  height: var(--pm-navbar-height);
  display: flex;
  align-items: center;
  padding: 0 16px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  flex-shrink: 0;
  overflow: hidden;
  white-space: nowrap;
}

.logo-icon {
  font-size: 24px;
  flex-shrink: 0;
  margin-right: 10px;
  filter: drop-shadow(0 0 6px rgba(88, 166, 255, 0.5));
}

.logo-title {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 1px;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

/* Scrollbar */
.sidebar-container :deep(.el-scrollbar) {
  flex: 1;
}

/* Menu样式覆盖 */
.sidebar-container :deep(.el-menu) {
  border-right: none;
}

.sidebar-container :deep(.el-menu-item),
.sidebar-container :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  font-size: 13px;
}

.sidebar-container :deep(.el-menu-item:hover),
.sidebar-container :deep(.el-sub-menu__title:hover) {
  background-color: rgba(255, 255, 255, 0.06) !important;
}

.sidebar-container :deep(.el-menu-item.is-active) {
  background-color: var(--pm-sidebar-bg-active) !important;
  color: var(--pm-sidebar-text-active) !important;
  border-right: 3px solid var(--pm-accent);
}

.sidebar-container :deep(.el-sub-menu .el-menu-item) {
  padding-left: 50px !important;
  min-width: 0;
}

/* 折叠状态下的弹出菜单 */
.sidebar-container.is-collapsed :deep(.el-sub-menu .el-menu-item) {
  padding-left: 20px !important;
}
</style>
