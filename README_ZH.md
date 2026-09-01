<div align="center">
  <img src="app/src/main/res/drawable/app_logo.png" alt="RPClient Logo" width="112" />

  # RPClient

  简体中文 | [English](README.md)

  一个面向 Android 的本地优先 AI 角色扮演聊天客户端。

  支持角色卡、世界书、单人对话、群聊、长期摘要记忆、Prompt 检查与多种 LLM 接口。

  <p>
    <a href="https://play.google.com/store/apps/details?id=me.kafuuneko.rpclient">
      <img src="https://play.google.com/intl/en_us/badges/images/generic/zh-cn_badge_web_generic.png" alt="Get it on Google Play" height="70" />
    </a>
    <a href="https://f-droid.org/packages/me.kafuuneko.rpclient/">
      <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-zh-cn.png" alt="Get it on F-Droid" height="80" />
    </a>
  </p>

  [项目简介](#项目简介) · [界面展示](#界面展示) · [功能特性](#功能特性) · [快速开始](#快速开始) · [参与贡献](#参与贡献)
</div>

## 项目简介

RPClient 使用 Kotlin 与 Jetpack Compose 编写，在 Android 设备上提供完整、可控的 AI 角色扮演体验。聊天记录、角色、世界书和应用配置默认保存在本地，模型请求则直接发送到用户配置的服务商。

项目参考了 SillyTavern 的角色卡、世界书、Prompt 和 Regex 脚本生态，并提供相应的导入、导出与兼容能力。RPClient 并非 SillyTavern 的 Android 移植版，两者在部分高级字段和运行行为上仍可能存在差异。

> [!WARNING]
> 项目仍在开发中，数据结构、界面和兼容行为可能继续调整。导入重要角色卡、世界书或 Regex 脚本前，建议保留原始文件并定期备份。

## 界面展示

<p align="center">
  <img src="metadata/images/home.jpg" alt="RPClient 主页" width="45%" />
  <img src="metadata/images/chat.jpg" alt="RPClient 聊天页面" width="45%" />
</p>
<p align="center">
  <img src="metadata/images/story.jpg" alt="RPClient 故事创作" width="45%" />
  <img src="metadata/images/settings.jpg" alt="RPClient 设置页面" width="45%" />
</p>

## 功能特性

### 对话

- 单角色对话与多角色群聊
- 流式输出、停止生成、重新生成和续写
- 用户视角生成（Impersonate）
- 从指定消息创建对话分支
- Markdown 消息渲染与思维内容折叠
- 自动或手动生成长期摘要记忆
- 会话级世界书条目选择

### 角色与世界书

- 创建、编辑、搜索和管理角色
- 导入 Character Card V1/V2 JSON 与 PNG 角色卡
- 导出 Character Card V2 JSON 或带元数据的 PNG
- 支持角色头像、多个开场白、示例对话和扩展字段
- 创建、编辑、导入和导出 SillyTavern 风格世界书
- 支持关键字扫描、递归触发、概率、优先级、深度和 Token 预算等配置

### 模型与 Prompt

- OpenAI Compatible API
- Google Gemini API
- Anthropic Messages API
- 内置 ChatGPT、Gemini、Claude、DeepSeek、Grok 和 OpenRouter 配置模板
- 支持自定义服务地址、模型、请求头与生成参数
- Prompt 预设、宏展开和协议相关的消息后处理
- Prompt Inspector：检查最终消息、Token 预算、世界书命中、Regex 处理与省略内容
- 调试模式：在本地记录并查看原始请求和响应 JSON

### 故事创作

- 连续正文编辑器，支持本地防抖自动保存与 revision 冲突保护
- 支持按章节持久保存引导的文档末尾续写，以及会话期内的多步撤销与恢复
- Story Memory、作者注释、Story 专属角色激活、世界书匹配与 Prompt Inspector
- 流式生成、停止、部分结果恢复、会话内多步撤销与恢复
- TXT/Markdown 导入导出及可迁移的 `.rpstory.json` 归档

### Regex 脚本

- 兼容常见 SillyTavern Regex 脚本 JSON
- Global 和 Character 两种作用域
- Source、Markdown 和 Prompt 三种执行模式
- 支持脚本排序、复制、测试、导入与导出
- 角色卡内嵌脚本默认不会自动获得执行授权

### 其他

- 本地优先的数据存储
- Material 3 与动态配色
- 简体中文、繁体中文、英语、日语、韩语、德语、法语、俄语、葡萄牙语（巴西）、意大利语、越南语和印尼语界面
- Android 8.0（API 26）及以上系统

## 快速开始

1. 安装并打开 RPClient（可通过 [Google Play](https://play.google.com/store/apps/details?id=me.kafuuneko.rpclient)、[F-Droid](https://f-droid.org/packages/me.kafuuneko.rpclient/) 或 [GitHub Releases](https://github.com/KafuuNeko/RPClient/releases) 获取）。
2. 进入“设置 > 模型配置”，选择已有模板或新建模型配置。
3. 填写 API Key、模型名称和服务地址，测试连接后启用该配置。
4. 创建角色，或导入已有的 JSON/PNG 角色卡。
5. 根据需要导入世界书并关联角色。
6. 新建单人会话或群聊开始对话，或进入“故事创作”创建连续正文。

API Key 仅保存在应用本地，但会随模型请求发送给所配置的服务商。请只使用可信的 API 地址和中转服务。

## AI 编码引导

使用 AI 辅助修改代码前，必须先阅读 [doc/coding-guidelines.md](doc/coding-guidelines.md)。该引导已按 RPClient 的 Feature + MVI 结构、Room 数据层、Koin/Kotpref 使用方式，以及角色卡、世界书、Prompt、Regex 脚本、LLM 请求和请求日志等领域规则整理。

开始编辑前只读取与任务相关的专题文档，完成后运行与改动风险匹配的最小验证。

## 参与贡献

欢迎提交 Issue 和 Pull Request。使用 AI 辅助编码时，请遵循 [AI 编码引导](doc/coding-guidelines.md)。

提交代码前建议至少保证代码可构建。

报告兼容性问题时，请尽量附上脱敏后的角色卡、世界书或 Regex JSON、使用的 Provider 协议、模型名称以及复现步骤。请勿在 Issue 中公开 API Key、私密对话或未经授权的角色资源。

## 隐私与免责声明

- RPClient 不提供模型服务，使用模型 API 产生的费用与内容责任由用户自行承担。
- 请求内容会发送到用户配置的模型服务商，使用前请阅读对应服务的隐私政策。
- 项目与 SillyTavern、OpenAI、Google、Anthropic、DeepSeek、xAI 及 OpenRouter 均无隶属或官方合作关系。
- 角色卡、世界书及其他导入内容的版权与使用授权由其提供者和使用者负责。

## 联系方式

- GitHub：[KafuuNeko/RPClient](https://github.com/KafuuNeko/RPClient)
- Email：kafuuneko@gmail.com
