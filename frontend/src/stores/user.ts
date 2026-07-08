import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore(
  'user',
  () => {
    const token = ref<string>('')
    const refreshToken = ref<string>('')
    const username = ref<string>('')
    const role = ref<string>('')

    const isLogin = computed(() => !!token.value)

    function setAuth(data: { accessToken: string; refreshToken: string; username?: string; role?: string }) {
      token.value = data.accessToken
      refreshToken.value = data.refreshToken
      if (data.username) username.value = data.username
      if (data.role) role.value = data.role
    }

    function logout() {
      token.value = ''
      refreshToken.value = ''
      username.value = ''
      role.value = ''
    }

    return { token, refreshToken, username, role, isLogin, setAuth, logout }
  },
  { persist: true },
)
