<template>
  <div class="log-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>定时任务日志</span>
        </div>
      </template>
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="任务">
          <el-input v-model="searchForm.taskId" placeholder="任务ID" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="运行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="taskLogs" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="taskName" label="任务名称" />
        <el-table-column prop="taskBeanName" label="Bean名称">
          <template #default="scope">
            {{ scope.row.taskBeanName || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="taskMethodName" label="方法名">
          <template #default="scope">
            {{ scope.row.taskMethodName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.status === 'RUNNING'" type="warning" size="small">运行中</el-tag>
            <el-tag v-else-if="scope.row.status === 'SUCCESS'" type="success" size="small">成功</el-tag>
            <el-tag v-else-if="scope.row.status === 'FAILED'" type="danger" size="small">失败</el-tag>
            <el-tag v-else size="small">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时(ms)" width="100">
          <template #default="scope">
            {{ scope.row.duration ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" />
        <el-table-column prop="errorMessage" label="错误信息" show-overflow-tooltip />
        <el-table-column label="操作" width="100">
          <template #default="scope">
            <el-button size="small" type="danger" @click="handleDelete(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
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
import api from '@/api'

const taskLogs = ref([])

const searchForm = reactive({
  taskId: '',
  status: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  itemCount: 0
})

const fetchTaskLogs = async () => {
  try {
    const params: any = {
      page: pagination.page,
      pageSize: pagination.pageSize
    }
    if (searchForm.taskId) {
      params.taskId = searchForm.taskId
    }
    if (searchForm.status) {
      params.status = searchForm.status
    }
    const response = await api.get('/task-log/list', { params })
    taskLogs.value = response.list
    pagination.itemCount = response.total
  } catch (error) {
    console.error('获取任务日志失败:', error)
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchTaskLogs()
}

const handleReset = () => {
  searchForm.taskId = ''
  searchForm.status = ''
  handleSearch()
}

const handleDelete = async (id: string) => {
  try {
    await api.delete(`/task-log/${id}`)
    fetchTaskLogs()
  } catch (error) {
    console.error('删除任务日志失败:', error)
  }
}

const handlePageChange = (page: number) => {
  pagination.page = page
  fetchTaskLogs()
}

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize
  pagination.page = 1
  fetchTaskLogs()
}

onMounted(() => {
  fetchTaskLogs()
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

.search-form {
  margin-bottom: 12px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
