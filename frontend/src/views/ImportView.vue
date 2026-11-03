<template>
  <div>
    <h2>导入资料</h2>
    <el-radio-group v-model="processMode" style="margin-bottom: 16px">
      <el-radio-button value="DISTILL">AI 提炼（结构化知识文档）</el-radio-button>
      <el-radio-button value="RAW">仅转 Markdown（原文入库）</el-radio-button>
    </el-radio-group>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>上传文件（文档 / 图片 / 音频，单个 ≤50MB，支持多选与文件夹）</template>
          <el-upload drag multiple :auto-upload="false" v-model:file-list="fileList"
                     accept=".docx,.pdf,.pptx,.xlsx,.xls,.csv,.txt,.md,.html,.htm,.json,.xml,.jpg,.jpeg,.png,.gif,.webp,.bmp,.tiff,.mp3,.wav,.m4a,.flac">
            <el-icon size="40"><UploadFilled /></el-icon>
            <div>拖拽一个或多个文件到此处，或点击选择</div>
          </el-upload>
          <el-space style="margin-top: 10px" wrap>
            <el-button type="primary" :disabled="!fileList.length" :loading="uploading"
                       @click="doBatchUpload(fileList.map((f) => ({ name: f.name, raw: f.raw })))">
              上传 {{ fileList.length }} 个文件
            </el-button>
            <el-button :loading="uploading" @click="pickDirectory">选择文件夹上传</el-button>
            <el-button v-if="fileList.length" @click="fileList = []">清空</el-button>
          </el-space>
          <input ref="dirInput" type="file" webkitdirectory multiple style="display: none"
                 @change="onDirectoryPicked" />
          <div style="margin-top: 8px; color: #909399; font-size: 12px">
            提示：图片仅提取 EXIF 等元数据（图中文字需 OCR）；音频经语音转写，CPU 上较慢
          </div>
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
const processMode = ref('DISTILL')
const submitting = ref(false)
const tasks = ref([])
const loading = ref(false)
const fileList = ref([])
const uploading = ref(false)
const dirInput = ref(null)
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

/** 批量上传（多选文件与目录选择共用）：逐个提交，汇总成功/失败，失败明细最多展示 3 条。 */
async function doBatchUpload(items) {
  if (!items.length || uploading.value) return
  uploading.value = true
  let ok = 0
  const errors = []
  for (const item of items) {
    try {
      await uploadFile(item.raw, processMode.value, { silent: true })
      ok++
    } catch (e) {
      errors.push(`${item.name}：${e.message}`)
    }
  }
  uploading.value = false
  fileList.value = []
  if (errors.length === 0) {
    ElMessage.success(`已提交 ${ok} 个导入任务`)
  } else {
    const detail = errors.slice(0, 3).join('；') + (errors.length > 3 ? ` 等 ${errors.length} 个失败` : '')
    ElMessage.warning(`已提交 ${ok} 个，失败 ${errors.length} 个（${detail}）`)
  }
  refresh()
}

function pickDirectory() {
  dirInput.value?.click()
}

function onDirectoryPicked(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  doBatchUpload(files.map((f) => ({ name: f.webkitRelativePath || f.name, raw: f })))
}

async function doSubmitUrl() {
  if (!url.value.trim()) return
  submitting.value = true
  try {
    await submitUrl(url.value.trim(), processMode.value)
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
