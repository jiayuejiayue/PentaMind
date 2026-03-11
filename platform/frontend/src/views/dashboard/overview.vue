<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :xs="12" :sm="6" v-for="stat in stats" :key="stat.title">
        <div class="stat-card" :style="{ borderTopColor: stat.color }">
          <div class="stat-info">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-title">{{ stat.title }}</div>
          </div>
          <div class="stat-icon" :style="{ color: stat.color }">
            <el-icon :size="36"><component :is="stat.icon" /></el-icon>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 中间内容区 -->
    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :xs="24" :sm="16">
        <div class="card">
          <div class="card-header">
            <h3>近期扫描活动</h3>
            <el-button text type="primary" size="small" @click="loadRecentScans">刷新</el-button>
          </div>
          <el-table :data="recentScans" style="width: 100%;" size="small" empty-text="暂无扫描记录" v-loading="scansLoading">
            <el-table-column prop="target" label="目标" min-width="160" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="scanTypeTag(row.type)">{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTag(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="vulns" label="发现" width="60" />
            <el-table-column prop="time" label="时间" width="160" />
          </el-table>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="card">
          <div class="card-header"><h3>漏洞分布</h3></div>
          <div class="vuln-list">
            <div class="vuln-item" v-for="v in vulnDist" :key="v.label">
              <div class="vuln-row">
                <span class="vuln-dot" :style="{ background: v.color }"></span>
                <span class="vuln-label">{{ v.label }}</span>
                <span class="vuln-count">{{ v.count }}</span>
              </div>
              <el-progress :percentage="v.pct" :color="v.color" :show-text="false" :stroke-width="6" />
            </div>
          </div>
        </div>

        <div class="card" style="margin-top:16px;">
          <div class="card-header"><h3>🤖 大模型配置</h3></div>
          <div class="llm-list">
            <div class="llm-item active-item">
              <div class="llm-header">
                <span class="llm-badge">当前激活</span>
                <el-tag type="success" size="small" effect="dark">运行中</el-tag>
              </div>
              <div class="llm-name">智谱 GLM-5</div>
              <div class="llm-detail">
                <span class="llm-key">模型</span><span class="llm-val">glm-5</span>
              </div>
              <div class="llm-detail">
                <span class="llm-key">接口</span><span class="llm-val llm-url">maas-api.ai-yuanjing.com</span>
              </div>
              <div class="llm-detail">
                <span class="llm-key">特性</span><span class="llm-val">支持推理思维链 (CoT)</span>
              </div>
            </div>
            <div class="llm-item">
              <div class="llm-header">
                <span class="llm-badge inactive">备用</span>
                <el-tag type="info" size="small">待激活</el-tag>
              </div>
              <div class="llm-name">恒脑 DAS AI</div>
              <div class="llm-detail">
                <span class="llm-key">智能体</span><span class="llm-val">86832399-...</span>
              </div>
              <div class="llm-detail">
                <span class="llm-key">接口</span><span class="llm-val llm-url">www.das-ai.com</span>
              </div>
              <div class="llm-detail">
                <span class="llm-key">切换</span><span class="llm-val">修改 active-provider 配置</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const overviewData = ref({})
const recentScans = ref([])
const tools = ref([])
const loading = ref(false)
const scansLoading = ref(false)
const toolsLoading = ref(false)
let refreshTimer = null

// 统计卡片 - 从 API 数据中计算
const stats = computed(() => [
  { title: '测试目标', value: overviewData.value.targetCount ?? '-', icon: 'Aim', color: '#1890ff' },
  { title: '已发现漏洞', value: overviewData.value.vulnCount ?? '-', icon: 'Warning', color: '#ff4757' },
  { title: '扫描任务', value: overviewData.value.taskCount ?? '-', icon: 'List', color: '#2ed573' },
  { title: '测试报告', value: overviewData.value.reportCount ?? '-', icon: 'DataAnalysis', color: '#ffa502' },
])

// 漏洞分布 - 从 API 数据中计算
const vulnDist = computed(() => {
  const d = overviewData.value
  const total = (d.criticalVulns || 0) + (d.highVulns || 0) + (d.mediumVulns || 0) + (d.lowVulns || 0)
  const pct = (v) => total > 0 ? Math.round((v / total) * 100) : 0
  return [
    { label: '严重', count: d.criticalVulns || 0, pct: pct(d.criticalVulns || 0), color: '#ff4757' },
    { label: '高危', count: d.highVulns || 0, pct: pct(d.highVulns || 0), color: '#ff6b35' },
    { label: '中危', count: d.mediumVulns || 0, pct: pct(d.mediumVulns || 0), color: '#ffd32a' },
    { label: '低危', count: d.lowVulns || 0, pct: pct(d.lowVulns || 0), color: '#2ed573' },
  ]
})

// API 调用
async function apiFetch(url) {
  try {
    const res = await fetch(url)
    const json = await res.json()
    return json.code === 200 ? json.data : null
  } catch (e) {
    console.warn('[Dashboard] API 请求失败:', url, e.message)
    return null
  }
}

async function loadOverview() {
  const data = await apiFetch('/api/dashboard/overview')
  if (data) overviewData.value = data
}

async function loadRecentScans() {
  scansLoading.value = true
  const data = await apiFetch('/api/dashboard/recent-scans')
  if (data) recentScans.value = data
  scansLoading.value = false
}

async function loadTools() {
  toolsLoading.value = true
  const data = await apiFetch('/api/dashboard/tools')
  if (data) tools.value = data
  toolsLoading.value = false
}

async function loadAll() {
  await Promise.all([loadOverview(), loadRecentScans(), loadTools()])
}

// 扫描类型标签颜色
function scanTypeTag(type) {
  const map = { 'FEATURE_SCAN': 'primary', 'PATH_SCAN': 'success', 'API_SCAN': 'warning', 'VULN_SCAN': 'danger', 'PARAM_FUZZ': 'info' }
  return map[type] || ''
}

// 状态标签
function statusTag(status) {
  const map = { 'COMPLETED': 'success', 'RUNNING': 'warning', 'PENDING': 'info', 'FAILED': 'danger' }
  return map[status] || ''
}

onMounted(() => {
  loadAll()
  // 每 30 秒自动刷新
  refreshTimer = setInterval(loadAll, 30000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.dashboard { width: 100%; }

.stat-card {
  background: var(--pm-bg-content);
  border: 1px solid var(--pm-border);
  border-top: 3px solid;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover { transform: translateY(-2px); box-shadow: var(--pm-shadow); }
.stat-value { font-size: 28px; font-weight: 700; color: var(--pm-text-primary); }
.stat-title { font-size: 13px; color: var(--pm-text-secondary); margin-top: 4px; }
.stat-icon { opacity: 0.6; }

.card {
  background: var(--pm-bg-content);
  border: 1px solid var(--pm-border);
  border-radius: 8px;
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.card-header h3 { font-size: 15px; font-weight: 600; }

.vuln-list { display: flex; flex-direction: column; gap: 14px; }
.vuln-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.vuln-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.vuln-label { flex: 1; font-size: 13px; color: var(--pm-text-secondary); }
.vuln-count { font-weight: 600; font-size: 14px; }

.tool-list { display: flex; flex-direction: column; gap: 12px; }
.tool-item { display: flex; align-items: flex-start; gap: 12px; padding: 12px; border-radius: 6px; background: var(--pm-bg-page); border-left: 3px solid #1890ff; }
.tool-info { display: flex; flex-direction: column; gap: 4px; }
.tool-name { font-size: 14px; font-weight: 600; color: var(--pm-text-primary); display:flex; align-items:center; gap: 6px;}
.tool-desc { font-size: 12px; color: var(--pm-text-secondary); line-height: 1.4; }
.version-tag { zoom: 0.8; }

/* 大模型配置卡片 */
.llm-list { display: flex; flex-direction: column; gap: 10px; }
.llm-item { padding: 12px; border-radius: 6px; background: var(--pm-bg-page); border-left: 3px solid #666; }
.active-item { border-left-color: #2ed573; background: rgba(46, 213, 115, 0.05); }
.llm-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.llm-badge { font-size: 11px; color: #2ed573; font-weight: 600; }
.llm-badge.inactive { color: #666; }
.llm-name { font-size: 14px; font-weight: 700; color: var(--pm-text-primary); margin-bottom: 6px; }
.llm-detail { display: flex; gap: 8px; margin-top: 3px; }
.llm-key { font-size: 11px; color: var(--pm-text-muted); min-width: 32px; }
.llm-val { font-size: 12px; color: var(--pm-text-secondary); }
.llm-url { color: #1890ff; font-family: monospace; font-size: 11px; }
</style>
