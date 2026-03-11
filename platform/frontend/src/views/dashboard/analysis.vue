<template>
  <div class="analysis-container">
    <el-card class="analysis-card" shadow="hover">
      <template #header>
        <div class="card-header">
           <div style="display:flex; justify-content: space-between; align-items: center; width: 100%;">
              <span>资产特性与智能攻击面预测拓扑</span>
              <div class="search-box">
                 <el-input 
                    v-model="targetUrl" 
                    placeholder="请输入测试目标，例如 http://example.com" 
                    clearable 
                    style="width: 350px">
                 </el-input>
                 <el-select v-model="contextHint" style="width: 150px; margin-left:10px" placeholder="已知业务上下文">
                     <el-option label="盲打未知域" value="" />
                     <el-option label="含SSO通用登录框" value="login" />
                     <el-option label="含文件上传点" value="upload,login" />
                 </el-select>
                 <el-button type="primary" icon="MagicStick" style="margin-left:10px;" :loading="analyzing" @click="handleAnalyze">
                    智能预测攻击面
                 </el-button>
              </div>
           </div>
        </div>
      </template>
      <div v-if="hasData" ref="chartRef" class="chart-container"></div>
      <el-empty v-else description="请在上方输入目标 URL 启动智能架构脉络推断" class="chart-container" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const chartRef = ref(null)
let chartInstance = null

const targetUrl = ref('')
const contextHint = ref('')
const analyzing = ref(false)
const hasData = ref(false)

async function request(url, options = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  return res.json()
}

/** 核心请求分析接口 */
async function handleAnalyze() {
    if (!targetUrl.value) {
        ElMessage.warning('请输入目标 URL')
        return
    }
    analyzing.value = true
    try {
        const res = await request('/api/analysis/predict', {
            method: 'POST',
            body: JSON.stringify({ url: targetUrl.value, context: contextHint.value })
        })
        
        if (res.code === 200 && res.data) {
            hasData.value = true
            ElMessage.success('拓扑推断完毕')
            // 等待 DOM 渲染后重绘 Echarts
            await nextTick()
            renderChart(res.data)
        } else {
            ElMessage.error(res.msg || '分析失败')
        }
    } catch (e) {
        console.error(e)
        ElMessage.error('与后端模型通信异常')
    } finally {
        analyzing.value = false
    }
}

/** 动态渲染双边树 */
const renderChart = (aiData) => {
  if (!chartRef.value) return
  
  // 销毁旧实例防止互相污染渲染
  if (chartInstance) {
      chartInstance.dispose()
  }

  chartInstance = echarts.init(chartRef.value)
  
  const commonSeriesProps = {
    type: 'tree',
    symbolSize: 18,
    initialTreeDepth: 2,
    expandAndCollapse: true,
    animationDuration: 550,
    animationDurationUpdate: 750,
    lineStyle: {
      color: '#60A9A6',
      curveness: 0.5,
      width: 2
    },
    itemStyle: {
      color: '#EE6666',
      borderColor: '#FAC858',
      borderWidth: 2
    }
  }

  const dataRight = {
      name: 'PentaMind Analyzer\n(' + new URL(targetUrl.value).hostname + ')',
      label: {
        position: 'top',
        align: 'center',
        verticalAlign: 'bottom',
        fontSize: 14,
        color: '#fff',
        backgroundColor: '#F56C6C',
        padding: [8, 16],
        borderRadius: 8
      },
      children: aiData.right || []
  }

  const dataLeft = {
      name: 'HIDDEN',
      label: { show: false }, 
      itemStyle: { opacity: 0, color: 'transparent', borderColor: 'transparent' }, 
      children: aiData.left || []
  }

  const option = {
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [
      {
        ...commonSeriesProps,
        data: [dataRight],
        top: '10%',
        left: '50%',
        bottom: '10%',
        right: '15%',
        orient: 'LR',
        label: {
          position: 'left',
          verticalAlign: 'middle',
          align: 'right',
          fontSize: 14,
          fontWeight: 'bold',
          color: '#e5eaf3',
          backgroundColor: '#2b2f3a',
          padding: [6, 12],
          borderRadius: 6,
          borderWidth: 1,
          borderColor: '#409EFF',
          shadowColor: 'rgba(238,102,102, 0.4)',
          shadowBlur: 10
        },
        leaves: {
          label: {
            position: 'right',
            verticalAlign: 'middle',
            align: 'left',
            color: '#A3B6CC',
            backgroundColor: 'transparent',
            borderColor: 'transparent',
            shadowColor: 'transparent',
            fontWeight: 'normal',
          }
        }
      },
      {
        ...commonSeriesProps,
        itemStyle: {
          color: '#409EFF',
          borderColor: '#67C23A',
          borderWidth: 2
        },
        data: [dataLeft],
        top: '10%',
        left: '15%',
        bottom: '10%',
        right: '50%',
        orient: 'RL',
        label: {
          position: 'right',
          verticalAlign: 'middle',
          align: 'left',
          fontSize: 14,
          fontWeight: 'bold',
          color: '#e5eaf3',
          backgroundColor: '#2b2f3a',
          padding: [6, 12],
          borderRadius: 6,
          borderWidth: 1,
          borderColor: '#67C23A',
          shadowColor: 'rgba(103,194,58, 0.4)',
          shadowBlur: 10
        },
        leaves: {
          label: {
            position: 'left',
            verticalAlign: 'middle',
            align: 'right',
            color: '#A3B6CC',
            backgroundColor: 'transparent',
            borderColor: 'transparent',
            shadowColor: 'transparent',
            fontWeight: 'normal',
          }
        }
      }
    ]
  }
  
  chartInstance.setOption(option)
}

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (chartInstance) {
    chartInstance.dispose()
  }
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.analysis-container {
  padding: 20px;
  height: calc(100vh - 84px);
  box-sizing: border-box;
}

.analysis-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.el-card__body) {
  flex: 1;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.card-header {
  font-weight: bold;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.search-box {
  display: flex;
  align-items: center;
}

.chart-container {
  flex: 1;
  width: 100%;
  min-height: 500px;
  background-color: var(--el-bg-color-page);
  border-radius: 4px;
}
</style>
