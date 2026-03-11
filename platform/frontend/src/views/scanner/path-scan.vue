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
          <el-button type="warning" icon="FolderChecked" @click="handleStartScan" style="margin-left: 20px;">发起路径扫描</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card" style="margin-top: 20px;">
      <el-table v-loading="loading" :data="pathList" border style="width: 100%">
        <el-table-column type="index" width="55" align="center" label="序号" />
        <el-table-column label="目标ID" prop="targetId" width="80" align="center" />
        <el-table-column label="发现路径 (URL/Path)" prop="url" min-width="250" show-overflow-tooltip>
          <template #default="scope">
            <el-link type="primary" :href="scope.row.url" target="_blank">{{ scope.row.url }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="HTTP 状态" prop="statusCode" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.statusCode)">{{ scope.row.statusCode || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="包大小" prop="contentLength" width="100" align="center">
           <template #default="scope">
            {{ scope.row.contentLength ? (scope.row.contentLength) + ' B' : '-' }}
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
const pathList = ref([])
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
    const res = await request(`/api/path/list?${params.toString()}`)
    if (res.code === 200) {
      pathList.value = res.data.records
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
    const res = await request(`/api/path/${row.id}`, { method: 'DELETE' })
    if (res.code === 200) {
      getList()
      ElMessage.success('删除成功')
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  }).catch(() => {})
}

function handleStartScan() {
  ElMessageBox.prompt('请输入需要进行目录爆破的基础 URL (例如 http://example.com):', '新建 PathFinder/Dirsearch 任务', {
    confirmButtonText: '开始扫描',
    cancelButtonText: '取消'
  }).then(({ value }) => {
    if (!value) return
    ElMessage.info(`引擎已下发针对 ${value} 的目录爆破任务，结果稍后写入数据库。`)
    // TODO: 调用后端真正调度工具
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