<template>
  <div class="navbar">
    <!-- 左侧：折叠按钮 + 面包屑 -->
    <div class="navbar-left">
      <div class="hamburger" @click="appStore.toggleSidebar()">
        <el-icon :size="20">
          <component :is="appStore.sidebar.opened ? 'Fold' : 'Expand'" />
        </el-icon>
      </div>

      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
          <span v-if="item.redirect === 'noRedirect' || item === breadcrumbs[breadcrumbs.length - 1]">
            {{ item.meta?.title }}
          </span>
          <router-link v-else :to="item.path">{{ item.meta?.title }}</router-link>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 右侧：操作按钮 -->
    <div class="navbar-right">
      <!-- 主题切换 -->
      <div class="navbar-action" @click="appStore.toggleTheme()" :title="appStore.theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'">
        <el-icon :size="18">
          <component :is="appStore.theme === 'dark' ? 'Sunny' : 'Moon'" />
        </el-icon>
      </div>

      <!-- 通知 -->
      <div class="navbar-action">
        <el-badge :value="3" :max="99">
          <el-icon :size="18"><Bell /></el-icon>
        </el-badge>
      </div>

      <!-- 全屏 -->
      <div class="navbar-action" @click="toggleFullScreen">
        <el-icon :size="18"><FullScreen /></el-icon>
      </div>

      <!-- 用户头像 -->
      <el-dropdown trigger="click">
        <div class="user-info">
          <el-avatar :size="28" style="background: var(--pm-accent);">
            <el-icon><User /></el-icon>
          </el-avatar>
          <span class="user-name">管理员</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item><el-icon><User /></el-icon> 个人中心</el-dropdown-item>
            <el-dropdown-item><el-icon><Setting /></el-icon> 系统设置</el-dropdown-item>
            <el-dropdown-item divided><el-icon><SwitchButton /></el-icon> 退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/modules/app'

const route = useRoute()
const appStore = useAppStore()

const breadcrumbs = computed(() => {
  const matched = route.matched.filter(r => r.meta?.title)
  return matched
})

function toggleFullScreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}
</script>

<style scoped>
.navbar {
  height: var(--pm-navbar-height);
  background: var(--pm-bg-navbar);
  border-bottom: 1px solid var(--pm-border);
  box-shadow: var(--pm-shadow);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  flex-shrink: 0;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hamburger {
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 4px;
  transition: background 0.2s;
  color: var(--pm-text-secondary);
}
.hamburger:hover {
  background: var(--pm-bg-hover);
  color: var(--pm-accent);
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.navbar-action {
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 4px;
  color: var(--pm-text-secondary);
  transition: all 0.2s;
}
.navbar-action:hover {
  background: var(--pm-bg-hover);
  color: var(--pm-accent);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
  color: var(--pm-text-primary);
}
.user-info:hover {
  background: var(--pm-bg-hover);
}
.user-name {
  font-size: 13px;
}

/* 面包屑颜色覆盖 */
:deep(.el-breadcrumb__inner) {
  color: var(--pm-text-secondary) !important;
}
:deep(.el-breadcrumb__separator) {
  color: var(--pm-text-muted) !important;
}
:deep(.el-breadcrumb__inner a) {
  color: var(--pm-text-secondary) !important;
}
:deep(.el-breadcrumb__inner a:hover) {
  color: var(--pm-accent) !important;
}
</style>
