<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <span class="logo">⬡</span>
        <h1>PentaMind</h1>
        <p>渗透测试管理平台</p>
      </div>
      <el-form :model="form" size="large">
        <el-form-item>
          <el-input v-model="form.username" prefix-icon="User" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" prefix-icon="Lock" placeholder="密码" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.remember">记住我</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width:100%;" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
const router = useRouter()
const form = reactive({ username: 'admin', password: '', remember: true })
function handleLogin() {
  router.push('/')
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0d1117, #161b22, #1a1a2e);
}
.login-card {
  background: var(--pm-bg-content);
  border: 1px solid var(--pm-border);
  border-radius: 12px;
  padding: 40px;
  width: 380px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.3);
}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.logo { font-size: 40px; }
.login-header h1 { font-size: 24px; margin: 8px 0 4px; }
.login-header p { color: var(--pm-text-secondary); font-size: 13px; }
</style>
