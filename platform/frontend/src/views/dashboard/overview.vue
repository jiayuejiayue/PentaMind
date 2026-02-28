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
            <el-button text type="primary" size="small">查看全部</el-button>
          </div>
          <el-table :data="recentScans" style="width: 100%;" size="small" empty-text="暂无扫描记录">
            <el-table-column prop="target" label="目标" min-width="160" />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.tagType">{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === '完成' ? 'success' : 'warning'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="vulns" label="漏洞" width="60" />
            <el-table-column prop="time" label="时间" width="140" />
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
          <div class="card-header"><h3>插件状态</h3></div>
          <div class="plugin-list">
            <div class="plugin-item">
              <el-icon :size="20" color="#58a6ff"><Monitor /></el-icon>
              <div class="plugin-info">
                <span class="plugin-name">Extension v1.1</span>
                <span class="plugin-status online">已连接</span>
              </div>
            </div>
            <div class="plugin-item">
              <el-icon :size="20" color="#2ed573"><Search /></el-icon>
              <div class="plugin-info">
                <span class="plugin-name">PathFinder v1.0</span>
                <span class="plugin-status online">运行中</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const stats = ref([
  { title: '测试目标', value: '12', icon: 'Aim', color: '#1890ff' },
  { title: '已发现漏洞', value: '47', icon: 'Warning', color: '#ff4757' },
  { title: '扫描任务', value: '8', icon: 'List', color: '#2ed573' },
  { title: '测试报告', value: '5', icon: 'DataAnalysis', color: '#ffa502' },
])

const recentScans = ref([
  { target: 'https://demo.example.com', type: '功能点扫描', tagType: 'primary', status: '完成', vulns: 12, time: '2026-02-28 08:30' },
  { target: 'https://api.example.com', type: '路径扫描', tagType: 'success', status: '完成', vulns: 3, time: '2026-02-28 07:15' },
  { target: 'https://admin.example.com', type: 'API识别', tagType: 'warning', status: '进行中', vulns: 0, time: '2026-02-28 09:00' },
])

const vulnDist = ref([
  { label: '严重', count: 5, pct: 25, color: '#ff4757' },
  { label: '高危', count: 12, pct: 60, color: '#ff6b35' },
  { label: '中危', count: 18, pct: 80, color: '#ffd32a' },
  { label: '低危', count: 12, pct: 50, color: '#2ed573' },
])
</script>

<style scoped>
.dashboard { max-width: 1200px; }

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

.plugin-list { display: flex; flex-direction: column; gap: 12px; }
.plugin-item { display: flex; align-items: center; gap: 12px; padding: 8px; border-radius: 6px; background: var(--pm-bg-page); }
.plugin-info { display: flex; flex-direction: column; }
.plugin-name { font-size: 13px; font-weight: 500; }
.plugin-status { font-size: 11px; }
.plugin-status.online { color: #2ed573; }
</style>
