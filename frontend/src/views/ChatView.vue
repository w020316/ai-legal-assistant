<script setup lang="ts">
// M1 阶段占位，M2 实现完整对话功能
</script>

<template>
  <div class="chat-view">
    <el-card shadow="never" class="placeholder">
      <el-empty description="智能问答功能将在 M2 阶段实现">
        <template #image>
          <el-icon :size="64" color="#1E3A5F"><ChatDotRound /></el-icon>
        </template>
      </el-empty>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.chat-view {
  height: 100%;
}
.placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
