export const useUserStore = defineStore('user', () => {
  // 注意：不能用 `Number(x) || null`，因为 type=0（超级管理员）是 falsy 值会被吞掉
  const storedUserId = localStorage.getItem('lexagent_userId')
  const storedType = localStorage.getItem('lexagent_userType')
  const token = ref(localStorage.getItem('lexagent_token') || '')
  const userId = ref(storedUserId !== null ? Number(storedUserId) : null)
  const userType = ref(storedType !== null ? Number(storedType) : null)
  const identifier = ref(localStorage.getItem('lexagent_identifier') || '')

  const isLoggedIn = computed(() => !!token.value)
  const isSuperAdmin = computed(() => userType.value === USER_TYPE.SUPER_ADMIN)
  const isAdmin = computed(() =>
    userType.value === USER_TYPE.SUPER_ADMIN || userType.value === USER_TYPE.ADMIN
  )

  function setLoginInfo(info) {
    token.value = info.token
    userId.value = info.userId
    userType.value = info.type
    identifier.value = info.identifier
    localStorage.setItem('lexagent_token', info.token)
    localStorage.setItem('lexagent_userId', String(info.userId))
    localStorage.setItem('lexagent_userType', String(info.type))
    localStorage.setItem('lexagent_identifier', info.identifier)
  }

  function clearLoginInfo() {
    token.value = ''
    userId.value = null
    userType.value = null
    identifier.value = ''
    localStorage.removeItem('lexagent_token')
    localStorage.removeItem('lexagent_userId')
    localStorage.removeItem('lexagent_userType')
    localStorage.removeItem('lexagent_identifier')
  }

  return {
    token,
    userId,
    userType,
    identifier,
    isLoggedIn,
    isSuperAdmin,
    isAdmin,
    setLoginInfo,
    clearLoginInfo
  }
})
