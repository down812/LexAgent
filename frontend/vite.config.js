import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [
      vue(),
      // ===== 自动导入 Vue / Vue Router / Pinia 的 API（ref/reactive/useRouter 等免手写） =====
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        resolvers: [ElementPlusResolver()],
        // 生成类型声明文件，IDE 自动补全（JS 项目也有效）
        dts: 'src/types/auto-imports.d.ts',
        // 自定义 composables / constants / stores / utils（格式化函数等）也自动导入
        dirs: ['src/composables', 'src/constants', 'src/stores', 'src/utils'],
        vueTemplate: true
      }),
      // ===== 自动注册 Element Plus 组件 + src/components 下的组件（免手写 import） =====
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/types/components.d.ts',
        dirs: ['src/components']
      })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 5173,
      open: true,
      proxy: {
        '/api': {
          target: env.VITE_API_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (p) => p.replace(/^\/api/, '')
        }
      }
    },
    // 构建优化：把大依赖拆成独立 chunk
    build: {
      chunkSizeWarningLimit: 800,
      rollupOptions: {
        output: {
          manualChunks: {
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
            'element-vendor': ['element-plus', '@element-plus/icons-vue']
          }
        }
      }
    }
  }
})
