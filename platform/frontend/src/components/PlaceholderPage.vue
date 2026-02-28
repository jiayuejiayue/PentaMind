<template>
  <div class="placeholder-page">
    <div class="page-header">
      <div class="page-icon">
        <el-icon :size="40"><component :is="icon" /></el-icon>
      </div>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>
    <div class="page-content">
      <el-empty description="功能开发中，敬请期待...">
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  icon: { type: String, default: 'Document' },
})
</script>

<style scoped>
.placeholder-page {
  max-width: 800px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  padding: 40px 20px 20px;
}

.page-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  border-radius: 16px;
  background: var(--pm-bg-content);
  border: 1px solid var(--pm-border);
  color: var(--pm-accent);
  margin-bottom: 16px;
}

.page-header h2 {
  font-size: 22px;
  margin-bottom: 8px;
  color: var(--pm-text-primary);
}

.page-header p {
  font-size: 14px;
  color: var(--pm-text-secondary);
}

.page-content {
  background: var(--pm-bg-content);
  border-radius: 8px;
  border: 1px solid var(--pm-border);
  padding: 40px 20px;
  margin-top: 16px;
}
</style>
