<template>
  <div style="display:flex;align-items:center;justify-content:center;height:100vh;flex-direction:column;background:var(--pm-bg-page);">
    <h1 style="font-size:80px;color:var(--pm-text-muted);margin-bottom:16px;">404</h1>
    <p style="color:var(--pm-text-secondary);margin-bottom:24px;">页面不存在或已被移除</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>
