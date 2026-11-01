<template>
  <el-container style="height: 100vh">
    <el-aside width="200px">
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/docs">知识文档</el-menu-item>
        <el-menu-item index="/import">导入</el-menu-item>
        <el-menu-item index="/chat">问答</el-menu-item>
      </el-menu>
    </el-aside>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>
