# 阅读释义 0.17.39

## 基线与范围

按要求先从 `30aded6`（v0.17.38 / 星标知识库分支）切到 `aa08be6`（v0.17.20，引入知识库的 `9cb8f6b` 的父提交），创建 `codex/contextual-reading` 后才开始修改。原 `codex/offline-ragflow` 分支保留，未改写历史。

普通 FreshRSS 星标保留。此版本不含 RAGFlow 功能与配置，不依赖 RAGFlow 服务。仅带回独立的 OpenAI 兼容接口、中文翻译默认提示词迁移和下载故障处理修复。本次未操作远端服务器。

## 行为

- 长按正文选词，使用 Android 浮动选择菜单解释或提问。WebView 和原生阅读均接入。
- 释义紧跟所选段落，沿用阅读字号、字体、颜色和背景，以引用式细竖线区分。继续问在同一区域展开输入，支持停止、重试、收起恢复。
- 请求包含选中文字、所在段落、当前已获取正文、标题、链接以及最近 8 轮对话；译文保留翻译前原文作为补充上下文。订阅仅提供摘要时，不声称读过未获取的全文。正文与原文合计超过 240000 字符明确拒绝，不静默截断。
- 设置名称改为“大模型”，摘要、翻译、解释分别选模型；API Key 默认隐藏，可用眼睛切换显示、复制。
- Gemini 解释可选 Google 搜索，默认关闭。模型按需决定搜索；回答显示来源和 Google 搜索建议。OpenAI 兼容路径本次仅提供正文上下文解释。
- 新数据库版本 13 接受 8–12 的升级路径。账号、订阅、文章、星标与 AI 内容表不变；退休表保留以便恢复，不再读取或同步。启动时取消旧知识库及离线后台任务。

## 验证

- `:app:testGithubDebugUnitTest`：28 项通过，无失败/跳过，覆盖模型接口兼容、翻译提示词、解释上下文、搜索来源与文章 HTML 安全。
- Debug 与 Release 编译、打包成功；Release 签名及版本信息已核验。最终 APK 内阅读脚本与当前源文件逐字节一致。
- 逐表对比历史 schema 8、9、10、11、12 与新 schema 13，保留表的字段、索引、外键一致。
- 浏览器加载实际 `reading-notes.js` 与阅读样式，393px 视口验证引用样式和原位追问。8 项 DOM 行为验证通过：不自动请求、自定义问句等待发送、重复词定位、追问上下文、重试清理、回答纯文本、收起恢复、切换选区。
- 本机无连接的 Android 设备，模拟器缺少虚拟化支持。尚未进行真机长按、软键盘遮挡及真实数据库安装升级验收；未使用真实 API Key 调用模型。

## Google 搜索费用（2026-09-18 官方资料）

Gemini 3.5 Flash 支持 Google Search grounding，无需换特殊模型或另配 Custom Search Key。Gemini API 项目需要付费层，在请求中启用 `google_search` 工具。

Gemini 3 系列共享每月 5000 次免费搜索请求；超出后每 1000 次 14 美元。一次用户提问可能触发多条搜索查询，按实际查询收费，模型 token 费用另计。

- https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash
- https://ai.google.dev/gemini-api/docs/pricing
- https://ai.google.dev/gemini-api/docs/generate-content/google-search
