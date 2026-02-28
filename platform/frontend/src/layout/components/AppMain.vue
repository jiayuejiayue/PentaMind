<template>
  <section class="app-main">
    <router-view v-slot="{ Component }">
      <transition name="fade-transform" mode="out-in">
        <keep-alive>
          <component :is="Component" />
        </keep-alive>
      </transition>
    </router-view>
  </section>
</template>

<style scoped>
.app-main {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: var(--pm-bg-page);
}

.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.2s ease;
}
.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}
.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(10px);
}
</style>
