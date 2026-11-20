# LexAgent 前端脚手架

基于 Vue 3 + Vite + Element Plus 的智能法律援助系统前端，采用工程化脚手架结构，方便后续功能页面的开发。

## 快速开始

```bash
cd frontend
npm install        # 安装依赖
npm run dev        # 开发服务器 http://localhost:5173
npm run build      # 生产构建 → dist/
npm run lint       # ESLint 代码检查 + 自动修复
npm run format     # Prettier 代码格式化
```

确保后端 Spring Boot 已启动在 `http://localhost:8080`。

---

## 脚手架核心机制：Auto-Import

本项目配置了 `unplugin-auto-import` + `unplugin-vue-components`，**大幅减少样板代码**。

### 无需手动 import 的内容（已全局可用）

| 来源 | 示例 |
|---|---|
| **Vue API** | `ref` `reactive` `computed` `watch` `onMounted` `nextTick` `defineProps` `defineEmits` |
| **Vue Router** | `useRoute` `useRouter` `createRouter` `createWebHistory` |
| **Pinia** | `defineStore` `storeToRefs` |
| **Element Plus 函数** | `ElMessage` `ElMessageBox` `ElNotification` `ElLoading` |
| **Element Plus 组件** | `<el-button>` `<el-table>` `<el-dialog>` …（全部自动注册） |
| **src/composables/** | `useTable` `usePagination` `useRequest` `useDialog` |
| **src/constants/** | `USER_TYPE` `FILE_STATUS` `AGENT_TYPE` `SSE_EVENT` … |
| **src/stores/** | `useUserStore` |
| **src/components/** | `<LegalAnswer>` `<AgentProgress>`（自动注册） |

### 仍需手动 import 的内容

```js
// API 请求函数
import { login } from '@/api/auth'
import { queryDatasets } from '@/api/knowledge'

// Element Plus 图标
import { Search, Plus, Delete } from '@element-plus/icons-vue'

// 第三方库
import { marked } from 'marked'
import DOMPurify from 'dompurify'
```

---

## 目录结构

```
src/
├── api/            # API 请求层（按后端 Controller 对应）
│   ├── request.js   # axios 实例 + token 拦截器 + 统一错误处理
│   ├── auth.js      # 认证接口（登录/注册/退出）
│   ├── user.js      # 用户接口（验证码/子账号）
│   ├── knowledge.js # 知识库接口（数据集/文件/检索）
│   └── chat.js      # 问诊接口（SSE/会话/消息/OCR）
├── components/      # 公共组件（自动注册，直接 <组件名 /> 使用）
│   ├── AgentProgress.vue  # SSE Agent 协作进度
│   └── LegalAnswer.vue    # 结构化法律回答渲染
├── composables/     # 通用逻辑 hooks（自动导入）
│   ├── useTable.js        # 表格：分页+查询+loading 三合一
│   ├── usePagination.js   # 通用分页
│   ├── useRequest.js      # 异步请求 loading 管理
│   └── useDialog.js       # 对话框状态
├── constants/       # 全局常量（自动导入）
│   └── index.js     # USER_TYPE / FILE_STATUS / AGENT_TYPE / SSE_EVENT / ...
├── layout/          # 主布局
│   └── Layout.vue   # 侧边栏 + 顶栏 + 路由出口
├── router/          # 路由配置 + 权限守卫
│   └── index.js
├── stores/          # Pinia 状态管理（自动导入）
│   └── user.js      # 用户登录态
├── styles/          # 全局样式
│   └── main.css
├── types/           # 自动生成的类型声明（勿手动编辑）
├── utils/           # 工具函数
│   ├── sse.js       # SSE 客户端（fetch + ReadableStream）
│   └── format.js    # 时间/大小格式化
├── views/           # 页面组件
│   ├── Login.vue
│   ├── Register.vue
│   ├── Consult.vue       # 法律智能问诊（SSE 核心页）
│   ├── Knowledge.vue     # 知识库管理
│   ├── Search.vue        # 检索测试
│   ├── Admin.vue         # 子账号管理
│   └── template/
│       └── PageTemplate.vue  # ★ 新增页面模板
├── App.vue
└── main.js
```

---

## 新增功能页面步骤

### 1. 复制模板

```bash
cp src/views/template/PageTemplate.vue src/views/YourPage.vue
```

### 2. 注册路由

在 `src/router/index.js` 的 `children` 数组中添加：

```js
{
  path: 'yourpage',
  name: 'YourPage',
  component: () => import('@/views/YourPage.vue'),
  meta: { title: '你的页面', icon: 'YourIcon' }        // requireAdmin / requireSuperAdmin 可选
}
```

### 3. 写 API（如果后端有新接口）

在 `src/api/` 下对应文件添加，或新建 `yourModule.js`：

```js
import request from './request'

export function listYourData(params, data) {
  return request.post('/your/api', data, { params })
}
```

### 4. 开发页面

直接在 `.vue` 文件中写业务逻辑，**无需关心 import ref/ElMessage/useRouter 等**，它们已全局可用。

---

## 已有功能页面

| 路由 | 页面 | 后端接口 | 说明 |
|---|---|---|---|
| `/login` | 登录 | `POST /auth/login` | 用户名/邮箱 + 密码 |
| `/register` | 注册 | `POST /user/email/code` + `POST /auth/register` | 邮箱验证码 |
| `/consult` | 法律问诊 | `POST /chat/consult`(SSE) + 会话管理 + `POST /chat/ocr` | **核心**：SSE 流式多 Agent 协作 |
| `/knowledge` | 知识库 | `/knowledge/dataset/*` + `/knowledge/file/*` | 数据集/文件/OCR 状态 |
| `/search` | 检索测试 | `POST /knowledge/search` + `/knowledge/search/hybrid` | 基础 vs 混合检索对比 |
| `/admin` | 子账号 | `/user/subaccount*` | 仅超管 |

## Composables 用法速查

```js
// useTable：表格分页查询三合一
const { tableData, loading, pageNum, pageSize, total, search, refresh } =
  useTable(api.queryFiles, { pageSize: 20 })
await search({ status: 'SUCCESS' })  // 带条件查询（自动重置第一页）
await refresh()                      // 刷新当前页

// useDialog：对话框开关
const { visible, open, close } = useDialog(() => { resetForm() })

// useRequest：单个异步请求 loading
const { loading, run } = useRequest(api.deleteItem)
const res = await run(id)
```

## 技术栈

Vue 3.4 + Vite 5 + Element Plus 2 + Pinia 2 + Vue Router 4 + Axios + marked + DOMPurify
