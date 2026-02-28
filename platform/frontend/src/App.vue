<template>
  <router-view />
</template>

<script setup>
import { useAppStore } from '@/store/modules/app'

const appStore = useAppStore()

// 初始化主题
appStore.initTheme()
</script>
