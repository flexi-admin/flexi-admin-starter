<template>
  <div class="log-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>操作日志</span>
        </div>
      </template>
      <el-table :data="operationLogs" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="operation" label="操作" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP地址" width="140" />
        <el-table-column label="请求参数" min-width="260" show-overflow-tooltip>
          <template #default="scope">
            <code class="params-cell">{{ formatParams(scope.row.params) }}</code>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="scope">
            {{ formatTime(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="scope">
            <el-button size="small" type="danger" @click="handleDeleteOperationLog(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无操作日志" />
        </template>
      </el-table>
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.itemCount"
          @update:page-size="handlePageSizeChange"
          @update:current-page="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

const operationLogs = ref<any[]>([])
const loading = ref(false)

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0
})

const formatTime = (t: any) => {
  if (!t) return ''
  const d = t instanceof Date ? t : new Date(t)
  if (isNaN(d.getTime())) return t
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const formatParams = (p: any) => {
  if (p === null || p === undefined || p === '') return '-'
  if (typeof p === 'string') {
    try {
      const parsed = JSON.parse(p)
      return JSON.stringify(parsed)
    } catch {
      return p
    }
  }
  return String(p)
}

const fetchOperationLogs = async () => {
  loading.value = true
  try {
    const response: any = await api.get('/log/operation/list', {
      params: {
        page: pagination.page,
        pageSize: pagination.pageSize
      }
    })
    operationLogs.value = response?.list ?? []
    pagination.itemCount = response?.total ?? 0
  } catch (error) {
    console.error('获取操作日志失败:', error)
  } finally {
    loading.value = false
  }
}

const handleDeleteOperationLog = async (id: number | string) => {
  try {
    await ElMessageBox.confirm('确定删除该操作日志吗？', '提示', {
      type: 'warning'
    })
    await api.delete(`/log/operation/${id}`)
    ElMessage.success('删除成功')
    fetchOperationLogs()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除操作日志失败:', error)
    }
  }
}

const handlePageChange = (page: number) => {
  pagination.page = page
  fetchOperationLogs()
}

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize
  pagination.page = 1
  fetchOperationLogs()
}

onMounted(() => {
  fetchOperationLogs()
})
</script>

<style scoped>
.log-container {
  padding: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.params-cell {
  font-family: Menlo, Consolas, monospace;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  word-break: break-all;
  white-space: normal;
  max-width: 100%;
}
</style>