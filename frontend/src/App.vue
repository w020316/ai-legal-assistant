<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'
// 根组件，仅承载路由出口
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
