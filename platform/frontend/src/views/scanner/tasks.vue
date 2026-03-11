<template>
  <div class="app-container">
    <!-- 工具栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="任务状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width:140px">
            <el-option label="执行中" value="RUNNING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标ID">
          <el-input v-model="queryParams.targetId" placeholder="关联目标ID" clearable style="width:120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="success" icon="Plus" @click="showCreateDialog" style="margin-left:12px">新建扫描任务</el-button>
          <el-button
            type="danger"
            icon="Delete"
            :disabled="selectedIds.length === 0"
            @click="handleBatchDelete"
            style="margin-left:8px"
          >批量删除 {{ selectedIds.length > 0 ? `(${selectedIds.length})` : '' }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 任务列表 -->
    <el-card shadow="never" class="table-card" style="margin-top: 16px;">
      <el-table v-loading="loading" :data="taskList" border style="width: 100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="46" align="center" :selectable="row => row.status !== 'RUNNING'" />
        <el-table-column type="index" width="55" align="center" label="#" />
        <el-table-column label="任务名称" prop="name" min-width="220" show-overflow-tooltip />
        <el-table-column label="工具" prop="scanType" width="160" align="center">
          <template #default="scope">
            <el-tag effect="plain" type="info">{{ toolLabel(scope.row.scanType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="110" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 'RUNNING'" type="primary" effect="dark" class="is-running">执行中</el-tag>
            <el-tag v-else-if="scope.row.status === 'COMPLETED'" type="success" effect="dark">已完成</el-tag>
            <el-tag v-else-if="scope.row.status === 'FAILED'" type="danger" effect="dark">失败</el-tag>
            <el-tag v-else type="info">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" prop="progress" min-width="160">
          <template #default="scope">
            <el-progress :percentage="scope.row.progress || 0"
              :status="scope.row.status === 'COMPLETED' ? 'success' : (scope.row.status === 'FAILED' ? 'exception' : '')" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="165" align="center" />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="View" @click="viewResult(scope.row)">结果</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)"
              :disabled="scope.row.status === 'RUNNING'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" :total="total"
          @size-change="getList" @current-change="getList" />
      </div>
    </el-card>

    <!-- 新建任务弹窗 -->
    <el-dialog v-model="createVisible" title="新建扫描任务" width="520px" :close-on-click-modal="false">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="选择目标" required>
          <el-select v-model="createForm.targetId" placeholder="选择已添加的测试目标" filterable style="width:100%">
            <el-option v-for="t in targetList" :key="t.id" :label="`[${t.id}] ${t.url}`" :value="t.id" />
          </el-select>
          <div style="font-size:11px;color:#909399;margin-top:4px;">
            在"信息收集 → 目标管理"中添加目标
          </div>
        </el-form-item>
        <el-form-item label="扫描工具" required>
          <el-select v-model="createForm.toolName" style="width:100%">
            <el-option v-for="t in TOOLS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务备注">
          <el-input v-model="createForm.remark" placeholder="可选，描述本次扫描目的" />
        </el-form-item>
        <!-- 根据工具显示额外参数 -->
        <template v-if="createForm.toolName === 'gobuster_dir'">
          <el-form-item label="字典">
            <el-select v-model="createForm.params.wordlist" style="width:100%">
              <el-option label="通用路径 (common)" value="common" />
              <el-option label="大字典 (big)" value="big" />
              <el-option label="API 路径 (api)" value="api" />
            </el-select>
          </el-form-item>
          <el-form-item label="扩展名">
            <el-input v-model="createForm.params.extensions" placeholder="php,html,bak,txt" />
          </el-form-item>
        </template>
        <template v-if="createForm.toolName === 'nmap_port_scan'">
          <el-form-item label="端口范围">
            <el-input v-model="createForm.params.ports" placeholder="1-1000 或 80,443,8080" />
          </el-form-item>
        </template>
        <template v-if="createForm.toolName === 'nuclei_vuln_scan'">
          <el-form-item label="漏洞等级">
            <el-select v-model="createForm.params.severity" style="width:100%">
              <el-option label="全部" value="" />
              <el-option label="严重 + 高危" value="critical,high" />
              <el-option label="中危" value="medium" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreateTask">提交任务</el-button>
      </template>
    </el-dialog>

    <!-- 结果详情抽屉 -->
    <el-drawer v-model="resultVisible" :title="`任务结果 — ${currentTask?.name}`" size="60%">
      <div v-if="resultLoading" style="text-align:center;padding:40px;">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p>正在加载结果...</p>
      </div>
      <div v-else-if="resultData.length === 0" style="padding:20px;">
        <el-empty description="暂无扫描结果" />
      </div>
      <div v-else>
        <!-- 原始输出结果 -->
        <el-alert type="success" :closable="false" style="margin-bottom:12px;">
          共发现 <b>{{ resultData.length }}</b> 条结果
        </el-alert>
        <el-table :data="resultData" border size="small" style="width:100%">
          <el-table-column prop="key" label="项目" min-width="200" show-overflow-tooltip>
            <template #default="scope">
              <span style="font-family:monospace">{{ scope.row.key }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="value" label="详情" min-width="200" show-overflow-tooltip />
          <el-table-column prop="risk" label="风险" width="80" align="center">
            <template #default="scope">
              <el-tag v-if="scope.row.risk" :type="scope.row.risk === 'HIGH' ? 'danger' : 'warning'" size="small">
                {{ scope.row.risk }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <!-- 如果是原始文本输出 -->
        <div v-if="rawOutput" style="margin-top:16px;">
          <div style="font-size:13px;color:#909399;margin-bottom:8px;">原始输出</div>
          <el-input type="textarea" :value="rawOutput" :rows="12" readonly
            style="font-family:monospace;font-size:12px;" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const taskList = ref([])
const targetList = ref([])
const total = ref(0)
const selectedIds = ref([])
const createVisible = ref(false)
const submitting = ref(false)
const resultVisible = ref(false)
const resultLoading = ref(false)
const resultData = ref([])
const rawOutput = ref('')
const currentTask = ref(null)
let pollTimer = null

// 支持的工具列表
const TOOLS = [
  { value: 'nmap_port_scan', label: '🔍 Nmap — 端口 & 服务指纹扫描' },
  { value: 'subfinder_domain', label: '🌐 Subfinder — 子域名被动收集' },
  { value: 'whatweb_fingerprint', label: '🧬 WhatWeb — Web 技术栈指纹' },
  { value: 'httpx_alive_detect', label: '💓 Httpx — HTTP 存活探测' },
  { value: 'gobuster_dir', label: '📂 Gobuster — 目录路径爆破' },
  { value: 'nuclei_vuln_scan', label: '☢️ Nuclei — PoC 漏洞扫描' },
]

const queryParams = reactive({ page: 1, size: 10, targetId: undefined, status: undefined })

const createForm = reactive({
  targetId: null,
  toolName: 'nmap_port_scan',
  remark: '',
  params: { wordlist: 'common', extensions: 'php,html,bak', ports: '1-1000', severity: '' }
})

function toolLabel(scanType) {
  return TOOLS.find(t => t.value === scanType)?.label?.split('—')[1]?.trim() || scanType
}

async function apiFetch(url, opts = {}) {
  const res = await fetch(url, { headers: { 'Content-Type': 'application/json' }, ...opts })
  return res.json()
}

async function loadTargets() {
  const res = await apiFetch('/api/target/list?page=1&size=100')
  if (res.code === 200) targetList.value = res.data?.records || []
}

async function getList(silent = false) {
  if (!silent) loading.value = true
  const p = new URLSearchParams({ page: queryParams.page, size: queryParams.size })
  if (queryParams.targetId) p.append('targetId', queryParams.targetId)
  if (queryParams.status) p.append('status', queryParams.status)
  try {
    const res = await apiFetch(`/api/task/list?${p}`)
    if (res.code === 200) {
      taskList.value = res.data?.records || []
      total.value = res.data?.total || 0
    }
  } catch (e) {
    if (!silent) ElMessage.error('获取任务列表失败')
  } finally {
    if (!silent) loading.value = false
  }
}

function handleQuery() { queryParams.page = 1; getList() }

function resetQuery() {
  queryParams.targetId = undefined
  queryParams.status = undefined
  handleQuery()
}

function handleSelectionChange(selection) {
  selectedIds.value = selection.map(r => r.id)
}

function showCreateDialog() {
  createForm.targetId = null
  createForm.toolName = 'nmap_port_scan'
  createForm.remark = ''
  createVisible.value = true
}

async function handleCreateTask() {
  if (!createForm.targetId) { ElMessage.warning('请选择目标'); return }
  submitting.value = true
  try {
    const res = await apiFetch('/api/task/start', {
      method: 'POST',
      body: JSON.stringify({
        targetId: createForm.targetId,
        toolName: createForm.toolName,
        remark: createForm.remark,
        params: createForm.params
      })
    })
    if (res.code === 200) {
      ElMessage.success('任务已提交，正在执行...')
      createVisible.value = false
      getList()
    } else {
      ElMessage.error(res.msg || '提交失败')
    }
  } catch (e) {
    ElMessage.error('请求异常')
  } finally {
    submitting.value = false
  }
}

async function viewResult(row) {
  currentTask.value = row
  resultVisible.value = true
  resultLoading.value = true
  resultData.value = []
  rawOutput.value = ''
  try {
    const res = await apiFetch(`/api/task/${row.id}/result`)
    if (res.code === 200 && res.data) {
      resultData.value = res.data.items || []
      rawOutput.value = res.data.rawOutput || ''
    }
  } catch (e) {
    ElMessage.error('获取结果失败')
  } finally {
    resultLoading.value = false
  }
}

function handleBatchDelete() {
  ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个任务？`, '批量删除', { type: 'warning' })
    .then(async () => {
      const results = await Promise.all(
        selectedIds.value.map(id => apiFetch(`/api/task/${id}`, { method: 'DELETE' }))
      )
      const successCount = results.filter(r => r.code === 200).length
      ElMessage.success(`成功删除 ${successCount} 个任务`)
      selectedIds.value = []
      getList()
    }).catch(() => {})
}

function handleDelete(row) {
  ElMessageBox.confirm('确定删除该任务？', '警告', { type: 'warning' })
    .then(async () => {
      const res = await apiFetch(`/api/task/${row.id}`, { method: 'DELETE' })
      if (res.code === 200) { getList(); ElMessage.success('删除成功') }
    }).catch(() => {})
}

function startPolling() {
  pollTimer = setInterval(() => {
    if (taskList.value.some(t => t.status === 'RUNNING')) getList(true)
  }, 2000)
}

onMounted(() => { getList(); loadTargets(); startPolling() })
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<style scoped>
.app-container { padding: 20px; }
.pagination-container { margin-top: 15px; display: flex; justify-content: flex-end; }
.is-running { animation: pulse 1.5s infinite ease-in-out; }
@keyframes pulse { 0%, 100% { opacity: 0.6; } 50% { opacity: 1; } }
</style>