/**
 * 对话框（Dialog）状态管理 composable
 *
 * 用法：
 *   const { visible, open, close } = useDialog()
 *   // 模板：<el-dialog v-model="visible">
 *
 * @param {function} onOpen 打开时的回调（常用于重置表单）
 */
export function useDialog(onOpen) {
  const visible = ref(false)

  function open() {
    if (onOpen) onOpen()
    visible.value = true
  }

  function close() {
    visible.value = false
  }

  return { visible, open, close }
}
