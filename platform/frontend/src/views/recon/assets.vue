<template>
  <div class="app-container">
    <!-- 顶部说明 -->
    <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
      <template #title>
        <span>🗂️ <b>目录扫描</b>（基于 Gobuster）— 枚举 Web 应用的隐藏路径、备份文件和管理后台</span>
      </template>
      <div style="margin-top:4px;font-size:12px;color:#909399;">
        先在<b>目标管理</b>中添加目标，然后在下方选择目标发起扫描，扫描结果实时更新。
      </div>
    </el-alert>

    <!-- 发起扫描 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" class="search-form">
        <el-form-item label="选择目标">
          <el-select v-model="scanForm.targetId" placeholder="选择已添加的目标" style="width:280px" filterable>
            <el-option v-for="t in targetList" :key="t.id" :label="`[${t.id}] ${t.url}`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="字典">
          <el-select v-model="scanForm.wordlist" style="width:200px">
            <el-option label="通用路径 (common)" value="common" />
            <el-option label="大字典 (big)" value="big" />
            <el-option label="API 路径 (api)" value="api" />
          </el-select>
        </el-form-item>
        <el-form-item label="扩展名">
          <el-input v-model="scanForm.extensions" placeholder="php,html,bak" style="width:160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="warning" icon="VideoPlay" :loading="scanning" @click="handleStartScan">发起目录扫描</el-button>
          <el-button icon="Search" @click="handleQuery">刷新结果</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 扫描结果表格 -->
    <el-card shadow="never" class="table-card" style="margin-top: 16px;">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span>扫描结果</span>
          <el-tag v-if="lastScanStatus === 'RUNNING'" type="primary" effect="dark" class="is-running">扫描中...</el-tag>
          <el-tag v-else-if="lastScanStatus === 'COMPLETED'" type="success" effect="dark">已完成</el-tag>
          <el-tag v-else-if="lastScanStatus === 'FAILED'" type="danger" effect="dark">扫描失败</el-tag>
        </div>
      </template>

      <el-table v-loading="loading" :data="resultList" border style="width: 100%">
        <el-table-column type="index" width="55" align="center" label="#" />
        <el-table-column label="发现路径" prop="path" min-width="260" show-overflow-tooltip>
          <template #default="scope">
            <span style="font-family:monospace;font-size:13px;">{{ scope.row.path }}</span>
          </template>
        </el-table-column>
        <el-table-column label="HTTP 状态码" prop="statusCode" width="130" align="center">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.statusCode)" effect="dark">{{ scope.row.statusCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" prop="size" width="120" align="center" />
        <el-table-column label="风险提示" prop="remark" min-width="200" show-overflow-tooltip>
          <template #default="scope">
            <span :style="{color: scope.row.risk === 'HIGH' ? '#ff4757' : '#909399'}">{{ scope.row.remark || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发现时间" prop="createTime" width="170" align="center" />
      </el-table>

      <!-- 空状态提示 -->
      <el-empty v-if="!loading && resultList.length === 0" description="暂无扫描结果，请先选择目标并发起扫描" />

      <div class="pagination-container" v-if="total > 0">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const scanning = ref(false)
const resultList = ref([])
const targetList = ref([])
const total = ref(0)
const lastScanStatus = ref('')
let pollTimer = null

const scanForm = reactive({
  targetId: null,
  wordlist: 'common',
  extensions: 'php,html,bak,txt'
})

const queryParams = reactive({ page: 1, size: 20, targetId: null })

async function apiFetch(url, opts = {}) {
  const res = await fetch(url, { headers: { 'Content-Type': 'application/json' }, ...opts })
  return res.json()
}

/** 加载目标列表（用于下拉选择） */
async function loadTargets() {
  const res = await apiFetch('/api/target/list?page=1&size=100')
  if (res.code === 200) targetList.value = res.data?.records || []
}

/** 查询扫描结果 */
async function getList(silent = false) {
  if (!silent) loading.value = true
  const params = new URLSearchParams({ page: queryParams.page, size: queryParams.size, tool: 'gobuster_dir' })
  if (queryParams.targetId) params.append('targetId', queryParams.targetId)
  try {
    const res = await apiFetch(`/api/scan-result/list?${params}`)
    if (res.code === 200) {
      resultList.value = res.data?.records || []
      total.value = res.data?.total || 0
    }
  } catch (e) {
    if (!silent) ElMessage.error('获取结果失败')
  } finally {
    if (!silent) loading.value = false
  }
}

/** 发起扫描 */
async function handleStartScan() {
  if (!scanForm.targetId) { ElMessage.warning('请先选择目标'); return }
  scanning.value = true
  lastScanStatus.value = 'RUNNING'
  try {
    const res = await apiFetch('/api/task/start', {
      method: 'POST',
      body: JSON.stringify({
        targetId: scanForm.targetId,
        toolName: 'gobuster_dir',
        params: { wordlist: scanForm.wordlist, extensions: scanForm.extensions }
      })
    })
    if (res.code === 200) {
      ElMessage.success('扫描任务已提交，正在执行...')
      queryParams.targetId = scanForm.targetId
      startPolling()
    } else {
      lastScanStatus.value = 'FAILED'
      ElMessage.error(res.msg || '提交失败')
    }
  } catch (e) {
    lastScanStatus.value = 'FAILED'
    ElMessage.error('请求异常')
  } finally {
    scanning.value = false
  }
}

function handleQuery() {
  queryParams.page = 1
  getList()
}

/** 根据 HTTP 状态码映射标签颜色 */
function statusType(code) {
  if (code >= 200 && code < 300) return 'success'
  if (code === 301 || code === 302) return 'warning'
  if (code === 403) return 'danger'
  if (code === 404) return 'info'
  return ''
}

/** 轮询刷新（任务运行中时自动刷新结果） */
function startPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    await getList(true)
    // 如果有新数据则说明扫描在进行，这里简单处理
    if (lastScanStatus.value === 'RUNNING') {
      lastScanStatus.value = resultList.value.length > 0 ? 'COMPLETED' : 'RUNNING'
    }
  }, 3000)
}

onMounted(() => {
  loadTargets()
  getList()
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.app-container { padding: 20px; }
.pagination-container { margin-top: 15px; display: flex; justify-content: flex-end; }
.is-running {
  animation: pulse 1.5s infinite ease-in-out;
}
@keyframes pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
</style>