<template>
  <div v-loading="loading">
    <el-page-header @back="goBack">
      <template #content>
        <el-space>
          <span>{{ doc.title }}</span>
          <el-tag :type="doc.status === 'CONFIRMED' ? 'success' : 'info'" size="small">
            {{ doc.status === 'CONFIRMED' ? '已确认' : '草稿' }}
          </el-tag>
          <el-tag size="small" effect="plain">v{{ doc.currentVersion }}</el-tag>
          <el-tag v-if="doc.processMode === 'RAW'" size="small" type="warning" effect="plain">仅转换</el-tag>
        </el-space>
      </template>
      <template #extra>
        <el-space>
          <el-button v-if="doc.status === 'DRAFT'" type="success" @click="doConfirm">确认无误</el-button>
          <el-button v-if="!editing" type="primary" @click="startEdit">编辑</el-button>
          <template v-else>
            <el-button @click="editing = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
          </template>
          <el-button @click="drawerVisible = true">版本历史</el-button>
          <el-button @click="openSource">查看原文</el-button>
          <el-button type="danger" plain @click="doDelete">删除</el-button>
        </el-space>
      </template>
    </el-page-header>

    <div style="margin: 8px 0; color: #909399; font-size: 13px">
      来源：<el-link :href="sourceLink" target="_blank" type="info">{{ doc.sourceLabel }}</el-link>
    </div>

    <el-space style="margin-bottom: 12px" wrap>
      <el-select v-model="docTags" multiple filterable allow-create default-first-option
                 placeholder="添加标签" style="width: 360px" @change="doSetTags" />
    </el-space>

    <MdEditor v-if="editing" v-model="draftContent" style="height: 65vh" />
    <MdPreview v-else :modelValue="doc.content" style="height: 65vh" />

    <el-drawer v-model="drawerVisible" title="版本历史" size="55%">
      <el-table :data="versions" highlight-current-row @current-change="selectVersion">
        <el-table-column prop="versionNo" label="版本" width="70">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column prop="changeNote" label="备注" show-overflow-tooltip />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString('zh-CN') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button size="small" :disabled="row.versionNo === doc.currentVersion"
                       @click.stop="doRollback(row.versionNo)">回滚到此版</el-button>
          </template>
        </el-table-column>
      </el-table>
      <h4 v-if="diffLines.length" style="margin-top: 16px">
        与当前版本（v{{ doc.currentVersion }}）的差异
      </h4>
      <div v-if="diffLines.length" class="diff-view">
        <div v-for="(line, i) in diffLines" :key="i" :class="['diff-line', line.type.toLowerCase()]">
          <span class="diff-sign">{{ sign(line.type) }}</span>{{ line.text }}
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="sourceVisible" :title="`原文：${sourceText.sourceLabel || ''}`" width="70%"
               top="6vh">
      <el-alert type="info" :closable="false" show-icon
                title="原文为只读视图，仅供对照纠错，不可编辑" style="margin-bottom: 12px" />
      <div v-loading="sourceLoading" class="source-view">
        <MdPreview v-if="sourceText.content" :modelValue="sourceText.content" />
        <el-empty v-else-if="!sourceLoading" description="原文内容为空" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MdEditor, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  confirmDoc, deleteDoc, diffVersions, getDoc, getDocSource, listVersions, rollback, saveDoc, setTags
} from '../api/docs'

const route = useRoute()
const router = useRouter()
const id = route.params.id

const doc = ref({})
const docTags = ref([])
const versions = ref([])
const diffLines = ref([])
const editing = ref(false)
const saving = ref(false)
const loading = ref(false)
const drawerVisible = ref(false)
const draftContent = ref('')

const sourceLink = computed(() =>
  doc.value.sourceLabel?.startsWith('http') ? doc.value.sourceLabel : null)

const sign = (type) => ({ ADD: '+ ', DEL: '- ', KEEP: '  ' }[type])

/** 从问答等页面跳入时返回来源页；直接打开详情时回文档列表 */
function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/docs')
  }
}

async function load() {
  loading.value = true
  try {
    doc.value = await getDoc(id)
    docTags.value = doc.value.tags || []
    versions.value = await listVersions(id)
  } finally {
    loading.value = false
  }
}

const sourceVisible = ref(false)
const sourceLoading = ref(false)
const sourceText = ref({ content: '', sourceLabel: '', sourceType: '' })

async function openSource() {
  sourceVisible.value = true
  sourceLoading.value = true
  try {
    sourceText.value = await getDocSource(id)
  } finally {
    sourceLoading.value = false
  }
}

function startEdit() {
  draftContent.value = doc.value.content
  editing.value = true
}

async function doSave() {
  saving.value = true
  try {
    const { value: note } = await ElMessageBox.prompt('保存备注（可留空）', '保存版本', {
      confirmButtonText: '保存', cancelButtonText: '取消', inputValue: ''
    }).catch(() => ({ value: null }))
    if (note === null) return
    doc.value = await saveDoc(id, draftContent.value, note || '')
    editing.value = false
    versions.value = await listVersions(id)
    ElMessage.success('已保存并重新向量化')
  } finally {
    saving.value = false
  }
}

async function doConfirm() {
  doc.value = await confirmDoc(id)
  ElMessage.success('已确认，可被检索与问答引用')
}

async function doSetTags(tags) {
  doc.value = await setTags(id, tags)
  docTags.value = doc.value.tags || []
  ElMessage.success('标签已更新')
}

async function doDelete() {
  await ElMessageBox.confirm(
    `将删除「${doc.value.title}」及其全部版本与索引，删除后不可恢复。确定删除？`,
    '删除确认',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
  )
  await deleteDoc(id)
  ElMessage.success('文档已删除')
  router.push('/docs')
}

async function selectVersion(version) {
  if (!version) return
  diffLines.value = await diffVersions(id, version.versionNo, doc.value.currentVersion)
}

async function doRollback(versionNo) {
  await ElMessageBox.confirm(`将内容回滚为 v${versionNo}（会生成一个新版本，历史不丢失）`, '回滚确认', {
    confirmButtonText: '回滚', cancelButtonText: '取消'
  })
  doc.value = await rollback(id, versionNo)
  versions.value = await listVersions(id)
  diffLines.value = []
  ElMessage.success(`已回滚，当前 v${doc.value.currentVersion}`)
}

onMounted(load)
</script>

<style scoped>
.diff-view {
  font-family: monospace;
  font-size: 13px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  max-height: 50vh;
  overflow: auto;
}
.diff-line { padding: 1px 8px; white-space: pre-wrap; }
.diff-line.add { background: #e8f7e8; color: #256b25; }
.diff-line.del { background: #fde8e8; color: #a82a2a; }
.diff-sign { display: inline-block; width: 20px; color: #909399; }
.source-view {
  max-height: 68vh;
  overflow: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px 12px;
}
</style>
