<template>
  <div class="agent-container">
    <el-row :gutter="20">
      <!-- 左侧控制栏 -->
      <el-col :span="6">
        <el-card shadow="hover" header="AI 智能体渗透配置">
          <el-form label-position="top">
            <el-form-item label="目标 URL">
              <el-input v-model="form.target" placeholder="https://example.com" />
            </el-form-item>
            
            <el-form-item label="执行阶段">
              <el-checkbox-group v-model="form.phases">
                <el-checkbox value="browser" label="browser" style="display:block">① Playwright 行为分析</el-checkbox>
                <el-checkbox value="recon" label="recon" style="display:block">② 自动化资产扫描</el-checkbox>
                <el-checkbox value="probe" label="probe" style="display:block">③ 敏感路径深度窗探</el-checkbox>
                <el-checkbox value="vuln" label="vuln" style="display:block">④ 功能点针对性渗透</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            
            <el-form-item label="测试账号 (可选)">
              <el-input v-model="form.username" placeholder="Username" style="margin-bottom: 10px;" />
              <el-input v-model="form.password" type="password" placeholder="Password" />
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" :disabled="running" style="width: 100%" @click="startPentest">
                <el-icon><VideoPlay /></el-icon> 启动智能渗透循环
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="hover" header="阶段实时日志" style="margin-top: 20px;">
          <div class="log-container">
            <div v-for="(log, idx) in logs" :key="idx" class="log-item">
              <span class="log-time">[{{ log.time }}]</span>
              <span :class="['log-msg', `log-${log.level}`]">{{ log.msg }}</span>
            </div>
            <div v-if="logs.length === 0" class="log-empty">等待任务启动...</div>
          </div>
        </el-card>
      </el-col>
      
      <!-- 右侧流程图DAG -->
      <el-col :span="18">
        <el-card shadow="hover" class="flow-card">
          <template #header>
            <div class="card-header">
              <span>动态流转链路视图 (DAG)</span>
              <el-tag :type="running ? 'warning' : 'info'">{{ running ? 'Agent 运行中...' : '节点空闲' }}</el-tag>
            </div>
          </template>
          <div class="flow-wrapper">
            <VueFlow 
              v-model="elements" 
              :default-viewport="{ x: 0, y: 0, zoom: 0.9 }" 
              fit-view-on-init
            >
              <Background pattern-color="#ccc" :size="1.5" />
              <Controls />
              
              <!-- 自定义节点渲染器 -->
              <template #node-custom="props">
                <div :class="['custom-node', `status-${props.data.status}`]">
                  <div class="node-icon">
                    <el-icon><component :is="props.data.icon" /></el-icon>
                  </div>
                  <div class="node-content">
                    <div class="node-label">{{ props.data.label }}</div>
                    <div class="node-status">{{ getStatusText(props.data.status) }}</div>
                  </div>
                </div>
              </template>
            </VueFlow>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 渗透结果报告侧边抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      title="智能体渗透评估报告"
      direction="rtl"
      size="50%"
    >
      <div v-if="reportLoading" class="drawer-loading" v-loading="true" element-loading-text="正在汇总全链路评估结论..."></div>
      <div v-else class="markdown-body report-content" v-html="reportHtml"></div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { marked } from 'marked' // 用于渲染 markdown 报告

// 如果你本地还未生效，使用 pnpm install @vue-flow/core @vue-flow/background @vue-flow/controls
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

const form = reactive({
  target: '',
  phases: ['browser', 'recon', 'probe', 'vuln'],
  username: '',
  password: ''
})

const running = ref(false)
const logs = ref([])

// 报告抽屉状态
const drawerVisible = ref(false)
const reportLoading = ref(false)
const reportHtml = ref('')
const currentTaskId = ref(null)

// 节点布局。Y轴相同表示横向并行
const initialElements = [
  { id: 'start', type: 'custom', position: { x: 50, y: 250 }, data: { label: '初始化', icon: 'Aim', status: 'pending' } },
  { id: 'browser', type: 'custom', position: { x: 250, y: 250 }, data: { label: 'Playwright 分析', icon: 'Monitor', status: 'pending' } },
  
  // 第二阶段：并行执行
  { id: 'nmap', type: 'custom', position: { x: 500, y: 100 }, data: { label: 'Nmap 端口', icon: 'Connection', status: 'pending' } },
  { id: 'subfinder', type: 'custom', position: { x: 500, y: 250 }, data: { label: '子域名收集', icon: 'Link', status: 'pending' } },
  { id: 'dirsearch', type: 'custom', position: { x: 500, y: 400 }, data: { label: '目录爆破', icon: 'Folder', status: 'pending' } },
  
  // 第三阶段
  { id: 'probe', type: 'custom', position: { x: 750, y: 250 }, data: { label: '敏感路径窗探', icon: 'View', status: 'pending' } },
  
  // 第四阶段：功能定向测试
  { id: 'vuln', type: 'custom', position: { x: 950, y: 250 }, data: { label: '针对性渗透', icon: 'Key', status: 'pending' } },
  
  // 最终生成报告
  { id: 'report', type: 'custom', position: { x: 1150, y: 250 }, data: { label: '最终报告', icon: 'Document', status: 'pending' } },
  
  // 拓扑连线
  { id: 'e1', source: 'start', target: 'browser', animated: false },
  
  { id: 'e2', source: 'browser', target: 'nmap', animated: false },
  { id: 'e3', source: 'browser', target: 'subfinder', animated: false },
  { id: 'e4', source: 'browser', target: 'dirsearch', animated: false },
  
  { id: 'e5', source: 'nmap', target: 'probe', animated: false },
  { id: 'e6', source: 'subfinder', target: 'probe', animated: false },
  { id: 'e7', source: 'dirsearch', target: 'probe', animated: false },
  
  { id: 'e8', source: 'probe', target: 'vuln', animated: false },
  { id: 'e9', source: 'vuln', target: 'report', animated: false },
]

// 深拷贝初始化节点信息
const elements = ref(JSON.parse(JSON.stringify(initialElements)))

// 工具函数
const getStatusText = (status) => {
  const map = { pending: '等待中', running: '进行中...', success: '完成', error: '失败' }
  return map[status] || '等待中'
}

const appendLog = (msg, level='info') => {
  const now = new Date()
  const time = `${now.getHours().toString().padStart(2,'0')}:${now.getMinutes().toString().padStart(2,'0')}:${now.getSeconds().toString().padStart(2,'0')}`
  logs.value.unshift({ time, msg, level }) // 最新的在顶部
}

const updateNodeStyles = (id, status) => {
  const node = elements.value.find(e => e.id === id)
  if (node) node.data.status = status
  
  // 更新连接线动画
  const edges = elements.value.filter(e => e.source === id)
  edges.forEach(e => {
    // 只有当源节点正在运行，或完成后连向下一个节点时才动画
    e.animated = (status === 'running' || status === 'success')
  })
}

// ========================
// 真实后端对接：调用 /api/agent/start 并监听 SSE 流
// ========================
let evtSource = null;

const fetchAgentReport = async (taskId) => {
  drawerVisible.value = true
  reportLoading.value = true
  try {
    const res = await fetch(`/api/agent/report/${taskId}`)
    const data = await res.json()
    if (data.code === 200 && data.data) {
      reportHtml.value = marked(data.data)
    } else {
      reportHtml.value = marked('### 尚未生成完整报告\n*可能是某阶段因严重报错闪退，或无漏洞发现*')
    }
  } catch (err) {
    reportHtml.value = `<div style="color:red">提取报告失败: ${err}</div>`
  } finally {
    reportLoading.value = false
  }
}

const startPentest = async () => {
  if (!form.target) {
    appendLog('请输入目标 URL', 'error')
    return
  }
  
  if (evtSource) {
    evtSource.close()
  }

  running.value = true
  logs.value = []
  elements.value = JSON.parse(JSON.stringify(initialElements))
  appendLog(`正在下发渗透指令至大模型任务总线...`, 'info')

  try {
    const res = await fetch('/api/agent/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        target: form.target,
        phases: form.phases,
        username: form.username,
        password: form.password
      })
    })
    
    const data = await res.json()
    // 后端 R.java 中成功返回 code 为 200
    if (data.code !== 200) {
      appendLog(`启动失败: ${data.msg}`, 'error')
      running.value = false
      return
    }

    const taskId = data.data
    currentTaskId.value = taskId
    appendLog(`AI 节点已分配任务号: Task-${taskId}，建立 SSE 流水线监听...`, 'success')

    // 建立 SSE 长连接监听
    evtSource = new EventSource(`/api/agent/stream/${taskId}`)

    evtSource.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data)
        // payload 格式: { level: 'info', nodeId: 'nmap', status: 'running', msg: '...' }
        if (payload.nodeId === 'CLOSE') {
          appendLog('接收到后端安全流关闭信令。', 'info')
          running.value = false
          evtSource.close()
          
          elements.value.forEach(e => {
              if (e.id.startsWith('e')) e.animated = false
          })
          
          // 流终结，尝试去后端拉取总结报告，2秒动画缓冲
          setTimeout(() => {
            fetchAgentReport(currentTaskId.value)
          }, 2000)
          
          return
        }

        // 1. 打印带级别的日志
        if (payload.msg) {
          appendLog(payload.msg, payload.level)
        }
        
        // 2. 状态扭转
        if (payload.nodeId && payload.status) {
          updateNodeStyles(payload.nodeId, payload.status)
        }

      } catch (err) {
        console.error("解析 EventSource Message 失败", err)
      }
    }

    evtSource.onerror = (err) => {
      appendLog('SSE 长连接中断，后端流程可能已结束或宕机', 'warning')
      running.value = false
      evtSource.close()
    }

  } catch (err) {
    appendLog(`渗透引擎通信异常: ${err}`, 'error')
    running.value = false
  }
}
</script>

<style scoped>
.agent-container {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.log-container {
  height: 380px;
  overflow: auto; /* 开启双向滚动条 */
  background-color: #1e1e24; /* 固定深色背景 */
  color: #abb2bf;
  padding: 12px;
  border-radius: 6px;
  font-family: "Fira Code", Menlo, Monaco, Consolas, "Courier New", monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: nowrap; /* 强制不折行，触发横向滚动条 */
  box-shadow: inset 0 2px 4px rgba(0,0,0,0.3);
}

/* 自定义滚动条样式 */
.log-container::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
.log-container::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.1);
}
.log-container::-webkit-scrollbar-thumb {
  background: #4b4b4b;
  border-radius: 4px;
}

:deep(.dark) .log-container {
  background-color: #141418;
}

.log-item {
  margin-bottom: 6px;
  border-bottom: 1px dashed rgba(255,255,255,0.1);
  padding-bottom: 4px;
}

.log-time {
  color: #5c6370;
  margin-right: 10px;
}
.log-empty {
  color: #5c6370;
  text-align: center;
  margin-top: 50px;
}

/* 黑底下的高对比度配色 (Dracula/OneDark风格) */
.log-info { color: #61afef; }
.log-success { color: #98c379; }
.log-warning { color: #e5c07b; }
.log-error { color: #e06c75; font-weight: bold; }

.flow-wrapper {
  height: 650px;
  width: 100%;
}

/* 节点定制样式 */
.custom-node {
  display: flex;
  align-items: center;
  padding: 12px 18px;
  border-radius: 8px;
  background: #ffffff;
  border: 2px solid #DCDFE6;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
  min-width: 160px;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

:deep(.dark) .custom-node {
  background: #2b2b36;
  border-color: #4C4D4F;
  color: #e5eaf3;
}

.custom-node.status-pending {
  border-color: #c0c4cc;
  opacity: 0.9;
}
.custom-node.status-running {
  border-color: #E6A23C;
  box-shadow: 0 0 15px rgba(230, 162, 60, 0.4);
  transform: translateY(-2px);
  /* background: linear-gradient(to right, #ffffff, #fdf6ec); */
}
:deep(.dark) .custom-node.status-running {
  background: #2b2b36;
}

.custom-node.status-success {
  border-color: #67C23A;
}
.custom-node.status-error {
  border-color: #F56C6C;
}

.node-icon {
  font-size: 24px;
  margin-right: 14px;
  display: flex;
}
.status-pending .node-icon { color: #909399; }
.status-running .node-icon { color: #E6A23C; animation: rotate 2s linear infinite; }
.status-success .node-icon { color: #67C23A; }
.status-error .node-icon { color: #F56C6C; }

.node-label {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 2px;
}
.node-status {
  font-size: 12px;
  color: #909399;
}
.status-success .node-status { color: #67C23A; }
.status-running .node-status { color: #E6A23C; }

@keyframes rotate {
  100% { transform: rotate(360deg); }
}

/* markdown 报告渲染框样式补充 */
.drawer-loading {
  height: 300px;
}
.report-content {
  padding: 10px 30px;
  line-height: 1.8;
  font-size: 14px;
}
:deep(.report-content h1), :deep(.report-content h2), :deep(.report-content h3) {
  margin-top: 24px;
  margin-bottom: 12px;
  color: #303133;
}
/* 暗黑模式下的大体适配 */
:deep(.dark .report-content) {
  color: #E4E7ED;
}
:deep(.dark .report-content h1), :deep(.dark .report-content h2), :deep(.dark .report-content h3) {
  color: #FFFFFF;
  border-bottom: 1px solid #36363c;
  padding-bottom: 6px;
}
:deep(.report-content pre) {
  background: #282c34; /* 深色代码块背景 */
  padding: 16px;
  border-radius: 6px;
  overflow: auto;
  color: #abb2bf; /* 护眼代码色 */
  font-family: Consolas, "Courier New", monospace;
}
/* markdown的内联代码 */
:deep(.report-content code) {
  background: rgba(0,0,0,0.05);
  padding: 3px 6px;
  border-radius: 4px;
  color: #e06c75;
}
:deep(.dark .report-content code) {
  background: rgba(255,255,255,0.1);
}
:deep(.report-content pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
}
</style>
