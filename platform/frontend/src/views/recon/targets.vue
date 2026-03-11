<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="目标名称">
          <el-input v-model="queryParams.name" placeholder="请输入目标名称或特征" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="success" icon="Plus" @click="handleAdd" style="margin-left: 20px;">新增目标</el-button>
          <el-button type="danger" icon="Delete" :disabled="selectedIds.length === 0" @click="handleBatchDelete" style="margin-left: 8px;">批量删除 <span v-if="selectedIds.length">({{ selectedIds.length }})</span></el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card" style="margin-top: 20px;">
      <el-table v-loading="loading" :data="targetList" border style="width: 100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="ID" prop="id" width="80" align="center" />
        <el-table-column label="目标名称" prop="name" min-width="150" show-overflow-tooltip />
        <el-table-column label="目标网址/IP" prop="url" min-width="200" show-overflow-tooltip>
          <template #default="scope">
            <el-link type="primary" :href="scope.row.url" target="_blank">{{ scope.row.url }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="资产类型" prop="type" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.type === 'WEB' ? '' : 'warning'">{{ scope.row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="120" align="center">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.status)">{{ statusFormat(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险评级" prop="riskLevel" width="100" align="center">
          <template #default="scope">
            <el-tag :type="riskType(scope.row.riskLevel)" effect="dark">{{ scope.row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发现漏洞" prop="vulnCount" width="100" align="center">
          <template #default="scope">
             <span :style="{ color: scope.row.vulnCount > 0 ? '#F56C6C' : 'inherit', fontWeight: scope.row.vulnCount > 0 ? 'bold' : 'normal'}">{{ scope.row.vulnCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" align="center" />
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="Position" @click="handleScan(scope.row)">发起扫描</el-button>
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑</el-button>
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

    <!-- 新增/修改目标对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="targetRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="目标URL/IP" prop="url">
          <el-input v-model="form.url" placeholder="示例: http://example.com 或 192.168.1.1" />
        </el-form-item>
        <el-form-item label="目标名称" prop="name">
          <el-input v-model="form.name" placeholder="自定义标识名称（选填）" />
        </el-form-item>
        <el-form-item label="资产类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择资产类型" style="width: 100%">
            <el-option label="Web 站点" value="WEB" />
            <el-option label="API 接口" value="API" />
            <el-option label="服务器主机" value="HOST" />
            <el-option label="其他应用" value="APP" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注描述" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(true)
const targetList = ref([])
const total = ref(0)
const title = ref('')
const open = ref(false)
const selectedIds = ref([])

const queryParams = reactive({
  page: 1,
  size: 10,
  name: undefined
})

const form = ref({})
const targetRef = ref(null)

const rules = reactive({
  url: [{ required: true, message: '目标地址不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '请选择资产类型', trigger: 'change' }]
})

// 请求 API 封装函数可以提出来，这里为了独立组件直接调用
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
  if (queryParams.name) params.append('name', queryParams.name)

  try {
    const res = await request(`/api/target/list?${params.toString()}`)
    if (res.code === 200) {
      targetList.value = res.data.records
      total.value = res.data.total
    }
  } catch (error) {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.page = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  queryParams.name = undefined
  handleQuery()
}

/** 取消按钮 */
function cancel() {
  open.value = false
  resetForm()
}

/** 表单重置 */
function resetForm() {
  form.value = {
    id: undefined,
    url: undefined,
    name: undefined,
    type: 'WEB',
    remark: undefined
  }
}

/** 新增按钮操作 */
function handleAdd() {
  resetForm()
  open.value = true
  title.value = '添加扫描目标'
}

/** 修改按钮操作 */
async function handleUpdate(row) {
  resetForm()
  const res = await request(`/api/target/${row.id}`)
  if (res.code === 200) {
    form.value = res.data
    open.value = true
    title.value = '修改扫描目标'
  }
}

/** 提交按钮 */
async function submitForm() {
  if (!targetRef.value) return
  await targetRef.value.validate(async (valid) => {
    if (valid) {
      // 如果没有填写名字，自动用URL host
      if (!form.value.name) {
        try {
          form.value.name = new URL(form.value.url).hostname || form.value.url
        } catch(e) {
          form.value.name = form.value.url
        }
      }
      
      const isUpdate = form.value.id != null
      const method = isUpdate ? 'PUT' : 'POST'
      
      try {
        const res = await request('/api/target', {
          method,
          body: JSON.stringify(form.value)
        })
        if (res.code === 200) {
          ElMessage.success(isUpdate ? '修改成功' : '新增成功')
          open.value = false
          getList()
        } else {
          ElMessage.error(res.msg || '操作失败')
        }
      } catch (error) {
        ElMessage.error('网络错误')
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  ElMessageBox.confirm('是否确认删除目标资产 "' + row.name + '"？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    const res = await request(`/api/target/${row.id}`, { method: 'DELETE' })
    if (res.code === 200) {
      getList()
      ElMessage.success('删除成功')
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  }).catch(() => {})
}

/** 表格多选回调 */
function handleSelectionChange(selection) {
  selectedIds.value = selection.map(row => row.id)
}

/** 批量删除 */
function handleBatchDelete() {
  ElMessageBox.confirm(`确认批量删除选中的 ${selectedIds.value.length} 条目标资产？此操作不可恢复！`, '警告', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await request('/api/target/batch', {
        method: 'DELETE',
        body: JSON.stringify(selectedIds.value)
      })
      if (res.code === 200) {
        ElMessage.success(`成功删除 ${selectedIds.value.length} 条目标`)
        selectedIds.value = []
        getList()
      } else {
        ElMessage.error(res.msg || '批量删除失败')
      }
    } catch (e) {
      ElMessage.error('网络错误')
    }
  }).catch(() => {})
}

/** 发起扫描 (接入后台引擎) */
function handleScan(row) {
  const toolOptions = [
    { label: '🔍 Nmap — 端口 & 服务指纹扫描', value: 'nmap_port_scan' },
    { label: '🌐 Subfinder — 子域名被动收集', value: 'subfinder_domain' },
    { label: '🧬 WhatWeb — Web 技术栈指纹识别', value: 'whatweb_fingerprint' },
    { label: '💓 Httpx — HTTP 存活探测', value: 'httpx_alive_detect' },
    { label: '📂 Gobuster — 目录路径爆破', value: 'gobuster_dir' },
    { label: '☢️ Nuclei — PoC 漏洞扫描', value: 'nuclei_vuln_scan' },
  ]
  ElMessageBox.confirm(
    `<div>
      <p style="margin-bottom:10px;color:#fff;">目标：<b>${row.url}</b></p>
      <el-select id="tool-select-dialog" style="width:100%">
        ${toolOptions.map((t, i) => `<option value="${t.value}">${t.label}</option>`).join('')}
      </el-select>
      <p style="margin-top:10px;font-size:12px;color:#909399;">选择后点击确认，任务将在后台异步执行，可在<b>漏洞扫描→测试任务</b>查看进度</p>
    </div>`,
    '选择扫描工具',
    {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '启动扫描',
      cancelButtonText: '取消',
      beforeClose: async (action, instance, done) => {
        if (action === 'confirm') {
          const sel = document.getElementById('tool-select-dialog')
          const toolName = sel?.value || 'nmap_port_scan'
          const res = await request('/api/task/start', {
            method: 'POST',
            body: JSON.stringify({ targetId: row.id, toolName, targetHost: row.url })
          })
          if (res.code === 200) {
            ElMessage.success(`已下发 ${toolName} 扫描任务！`)
          } else {
            ElMessage.error(res.msg || '下发失败')
          }
        }
        done()
      }
    }
  ).catch(() => {})
}

// 格式化函数
function statusFormat(status) {
  const map = { 0: '待测试', 1: '测试中', 2: '已完成' }
  return map[status] || '未知'
}
function statusType(status) {
  const map = { 0: 'info', 1: 'warning', 2: 'success' }
  return map[status] || 'info'
}
function riskType(risk) {
  const map = { 'CRITICAL': 'danger', 'HIGH': 'warning', 'MEDIUM': 'primary', 'LOW': 'info', 'INFO': 'info' }
  return map[risk] || 'info'
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