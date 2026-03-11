<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="关联目标 ID">
          <el-input v-model="queryParams.targetId" placeholder="基于目标ID搜索" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="warning" icon="MagicStick" @click="handleStartScan" style="margin-left: 20px;">发起子域名收集</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card" style="margin-top: 20px;">
      <el-table v-loading="loading" :data="subdomainList" border style="width: 100%">
        <el-table-column type="index" width="55" align="center" label="序号" />
        <el-table-column label="目标ID" prop="targetId" width="80" align="center" />
        <el-table-column label="子域名 (Domain)" prop="domain" min-width="200" show-overflow-tooltip>
          <template #default="scope">
            <el-link type="primary" :href="'http://' + scope.row.domain" target="_blank">{{ scope.row.domain }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="解析 IP 地址" prop="ipList" min-width="180" show-overflow-tooltip>
           <template #default="scope">
            <span style="font-family: monospace;">{{ scope.row.ipList }}</span>
          </template>
        </el-table-column>
        <el-table-column label="HTTP 状态" prop="statusCode" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.statusCode)">{{ scope.row.statusCode || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="页面标题 (Title)" prop="title" min-width="200" show-overflow-tooltip />
        <el-table-column label="发现来源 (Source)" prop="source" width="120" align="center" />
        <el-table-column label="发现时间" prop="createTime" width="170" align="center" />
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(true)
const subdomainList = ref([])
const total = ref(0)

const queryParams = reactive({
  page: 1,
  size: 10,
  targetId: undefined
})

async function request(url, options = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  return res.json()
}

/** 查询列表 */
async function getList() {
  loading.value = true
  const params = new URLSearchParams()
  params.append('page', queryParams.page)
  params.append('size', queryParams.size)
  if (queryParams.targetId) params.append('targetId', queryParams.targetId)

  try {
    const res = await request(`/api/subdomain/list?${params.toString()}`)
    if (res.code === 200) {
      subdomainList.value = res.data.records
      total.value = res.data.total
    }
  } catch (error) {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.page = 1
  getList()
}

function resetQuery() {
  queryParams.targetId = undefined
  handleQuery()
}

function handleDelete(row) {
  ElMessageBox.confirm('是否确认删除记录？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    const res = await request(`/api/subdomain/${row.id}`, { method: 'DELETE' })
    if (res.code === 200) {
      getList()
      ElMessage.success('删除成功')
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  }).catch(() => {})
}

function handleStartScan() {
  ElMessageBox.prompt('请输入需要枚举的主域名（例如 example.com）：', '🌐 发起 Subfinder 子域名收集', {
    confirmButtonText: '开始收集',
    cancelButtonText: '取消',
    inputPlaceholder: '例如: example.com'
  }).then(async ({ value }) => {
    if (!value?.trim()) return
    const res = await request('/api/task/start', {
      method: 'POST',
      body: JSON.stringify({
        targetId: 0,
        toolName: 'subfinder_domain',
        targetHost: value.trim()
      })
    })
    if (res.code === 200) {
      ElMessage.success('子域名收集任务已下发！可在 漏洞扫描→测试任务 中查看进度和结果。')
    } else {
      ElMessage.error(res.msg || '下发任务失败')
    }
  }).catch(() => {})
}

function getStatusType(code) {
  if (!code) return 'info'
  if (code >= 200 && code < 300) return 'success'
  if (code >= 300 && code < 400) return 'warning'
  if (code >= 400) return 'danger'
  return 'info'
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}
.pagination-container {
  margin-top: 15px;
  display: flex;
  justify-content: flex-end;
}
</style>