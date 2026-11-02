<template>
  <div>
    <h2>知识文档</h2>
    <el-space style="margin-bottom: 12px" wrap>
      <el-select v-model="filters.status" placeholder="状态" clearable style="width: 130px" @change="load">
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已确认" value="CONFIRMED" />
      </el-select>
      <el-select v-model="filters.tag" placeholder="标签" clearable style="width: 160px" @change="load">
        <el-option v-for="t in tags" :key="t" :label="t" :value="t" />
      </el-select>
      <el-input v-model="filters.keyword" placeholder="搜索标题/摘要" clearable style="width: 220px"
                @change="load" />
    </el-space>

    <el-table :data="page.content" v-loading="loading" @row-click="(row) => open(row.id)" style="cursor: pointer">
      <el-table-column prop="title" label="标题" show-overflow-tooltip />
      <el-table-column prop="summary" label="摘要" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'CONFIRMED' ? 'success' : 'info'">
            {{ row.status === 'CONFIRMED' ? '已确认' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="版本" width="70">
        <template #default="{ row }">v{{ row.currentVersion }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ new Date(row.updatedAt).toLocaleString('zh-CN') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" type="danger" plain
                     @click.stop="doDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px" layout="total, prev, pager, next"
                   :total="page.totalElements" :page-size="20"
                   :current-page="page.number + 1" @current-change="(p) => load(p - 1)" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { allTags, deleteDoc, listDocs } from '../api/docs'

const router = useRouter()
const filters = reactive({ status: null, tag: null, keyword: null })
const tags = ref([])
const page = ref({ content: [], totalElements: 0, number: 0 })
const loading = ref(false)

async function load(pageNo = 0) {
  loading.value = true
  try {
    page.value = await listDocs({ ...filters, page: pageNo, size: 20 })
  } finally {
    loading.value = false
  }
}

function open(id) {
  router.push(`/docs/${id}`)
}

async function doDelete(row) {
  await ElMessageBox.confirm(
    `将删除「${row.title}」及其全部版本与索引，删除后不可恢复。确定删除？`,
    '删除确认',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
  )
  await deleteDoc(row.id)
  ElMessage.success('文档已删除')
  load(page.value.number)
}

onMounted(async () => {
  tags.value = await allTags()
  load()
})
</script>
