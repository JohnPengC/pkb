<template>
  <div>
    <h2>导入资料</h2>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>上传文件（docx / pdf / pptx / xlsx / csv / txt / md，≤50MB）</template>
          <el-upload drag :show-file-list="false" :http-request="doUpload" accept=".docx,.pdf,.pptx,.xlsx,.csv,.txt,.md">
            <el-icon size="40"><UploadFilled /></el-icon>
            <div>拖拽文件到此处，或点击选择</div>
          </el-upload>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>导入网址</template>
          <el-input v-model="url" placeholder="https://..." @keyup.enter="doSubmitUrl">
            <template #append>
              <el-button :loading="submitting" @click="doSubmitUrl">导入</el-button>
            </template>
          </el-input>
        </el-card>
      </el-col>
    </el-row>

    <h3 style="margin-top: 24px">导入任务</h3>
    <el-table :data="tasks" v-loading="loading">
      <el-table-column prop="sourceLabel" label="来源" show-overflow-tooltip />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="错误" show-overflow-tooltip />
      <el-table-column prop="updatedAt" label="更新时间" width="200">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button v-if="row.status === 'FAILED'" size="small" @click="doRetry(row.id)">重试</el-button>
          <el-button v-if="['COMPLETED', 'FAILED'].includes(row.status)" size="small" type="danger"
                     plain @click="doDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteTask, listTasks, retryTask, submitUrl, uploadFile } from '../api/ingest'

const url = ref('')
const submitting = ref(false)
const tasks = ref([])
const loading = ref(false)
let timer = null

const STATUS_TEXT = {
  PENDING: '排队中', EXTRACTING: '解析中', DISTILLING: '提炼中',
  COMPLETED: '已完成', FAILED: '失败'
}
const statusText = (s) => STATUS_TEXT[s] || s
const statusType = (s) => ({ COMPLETED: 'success', FAILED: 'danger', PENDING: 'info' }[s] || 'warning')
const formatTime = (iso) => new Date(iso).toLocaleString('zh-CN')

async function refresh() {
  tasks.value = await listTasks()
}

async function doUpload({ file }) {
  await uploadFile(file)
  ElMessage.success('已提交导入任务')
  refresh()
}

async function doSubmitUrl() {
  if (!url.value.trim()) return
  submitting.value = true
  try {
    await submitUrl(url.value.trim())
    ElMessage.success('已提交导入任务')
    url.value = ''
    refresh()
  } finally {
    submitting.value = false
  }
}

async function doRetry(id) {
  await retryTask(id)
  refresh()
}

async function doDelete(row) {
  const extra = row.status === 'COMPLETED'
    ? '，其生成的知识文档（含全部版本）也将一并删除'
    : ''
  await ElMessageBox.confirm(
    `将删除导入记录「${row.sourceLabel}」${extra}，删除后同内容文件可重新导入。确定删除？`,
    '删除确认',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
  )
  await deleteTask(row.id)
  ElMessage.success('导入记录已删除')
  refresh()
}

onMounted(() => {
  loading.value = true
  refresh().finally(() => (loading.value = false))
  timer = setInterval(refresh, 2000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>
