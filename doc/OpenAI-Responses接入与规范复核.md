# OpenAI Responses 接入与规范复核

复核日期：2026-10-16。本文记录 RPClient 的原生 Responses 协议契约、配置方式和验证边界。

## 官方规范依据

- [Create a response](https://developers.openai.com/api/reference/resources/responses/methods/create)：HTTP 端点、输入和输出 Item、请求参数、状态与用量字段。
- [Responses streaming events](https://developers.openai.com/api/reference/resources/responses/streaming-events)：SSE 事件、增量位置、Item 快照与响应终态。
- [Reasoning](https://developers.openai.com/api/docs/guides/reasoning)：推理摘要、输出预算、无状态上下文和 assistant phase。
- [Latest model guide](https://developers.openai.com/api/docs/guides/latest-model)：当前 GPT-6 系列及推理、采样和 logprobs 参数限制。
- [Images and vision](https://developers.openai.com/api/docs/guides/images-vision)：`input_image` 与 Base64 data URL。

以上为此次核对的在线官方文档快照，模型名和模型能力可能继续变化。协议实现依据原生字段和事件，模型名称只影响保守的参数默认值及本地 Token 估算。

## 应用接入范围

`LLMProviderProtocol.OpenAIResponses` 是独立协议，由 `OpenAIResponsesLLMClient`、请求编码器、响应编码器及 SSE 状态机实现。单聊、群聊、故事生成和各类摘要均通过现有 `LLMRepository` / `LLMClientFactory` 进入同一个客户端。

新建 OpenAI 模板使用 `https://api.openai.com/v1`、`gpt-6-luna`、`reasoning.effort: low`，默认关闭 temperature/top_p 的发送并使用服务端用量。用户可从模型列表选择自己的账号可用模型。模型目录复用 OpenAI 的 `GET /models` 资源读取器。

已保存配置保持原协议。用户可以在模型配置的高级选项中选择 **OpenAI Responses**，或应用 OpenAI 模板。切换协议后应使用原生扩展参数；表单会拒绝 `messages`、`max_tokens`、`response_format`、`reasoning_effort` 等旧请求字段。

协议枚举追加新值，数据库以名称保存，因此无需修改 Room 表结构。模型配置、请求日志和 Token 统计沿用现有存储与导出链路。

## 请求契约

| 内容 | 实际发送行为 |
| --- | --- |
| 地址与认证 | `POST {baseUrl}/responses`，Bearer API Key，可使用已有自定义请求头功能 |
| 文本历史 | `input` 中的 `message` Item，内容为 `input_text`，保留角色、顺序及正文空白 |
| 世界书与系统指令 | 保留在最终 Prompt 中的 `system` 位置，不合并到顶层 instructions |
| assistant 历史 | 使用 EasyInputMessage，标记 `phase: final_answer`；只有最终回复进入本地正文 |
| 图片 | `input_image`、`image_url: data:<mime>;base64,...`、`detail: auto`，保持图文交错顺序 |
| 输出额度 | `max_output_tokens`，包含推理 Token，来自现有最大输出设置 |
| 流式 | `stream: true`，接受 `text/event-stream` |
| 上下文 | 每轮发送本地最终完整 input，`store: false`、`truncation: disabled` |
| 采样 | 已知推理模型或显式设置非 none 推理时，移除 temperature、top_p、top_logprobs 及 include 中的输出 logprobs |
| 推理显示 | 请求需要捕获推理且为已知支持摘要的模型时，补 `reasoning.summary: auto`；显式高级 JSON 优先 |

本地聊天支持编辑、重生成、世界书注入、摘要替换与 Prompt 预算裁剪，因此使用手动上下文模式。`previous_response_id`、`conversation`、`background`、`store` 和 `truncation` 不能由高级 JSON 改写，以保证检查器与实际请求一致。加密推理 Item 不持久化、不作为聊天正文回放。

请求体扩展继续采用已有 JSON Merge Patch 语义。原生配置示例：

```json
{
  "reasoning": {
    "effort": "low",
    "summary": "auto"
  },
  "text": {
    "verbosity": "medium"
  }
}
```

结构化输出使用 `text.format`。服务端托管工具可通过原生 `tools` 配置，例如 `web_search`；正文仍从最终 message 中读取。高级配置必须符合所选模型的能力，客户端不会把所有模型的特有参数强行转换为统一参数。

RPClient 当前提供文本与图片聊天，不提供通用客户端工具执行器。需要调用方执行的 function、custom、computer、shell、apply_patch 或 MCP approval Item 会明确失败，避免把等待工具执行误报成回复完成。此协议不把 Realtime、文件生成或音频会话当成文本生成流程。

## 响应和流状态

原始 HTTP 响应从 `output[]` 提取 `message.content[]` 的 `output_text` 与 `refusal`。顶层 `output_text` 是 SDK 辅助聚合属性，HTTP 编码器不依赖它。`reasoning.summary[].text` 与详细推理内容分别保留原有推理显示语义。

SSE 以空行分帧，支持多行 data、BOM、注释心跳及尾帧。未知事件可跳过，非法 JSON 和缺失响应终态不能视为成功。

| 事件或状态 | 处理 |
| --- | --- |
| `response.output_item.added` | 登记 phase，`commentary` 进度说明不进入最终聊天正文 |
| `response.output_text.delta` / `response.refusal.delta` | 保留空白和字面量 `null`，按原生位置追加正文 |
| `response.reasoning_summary_text.delta` / `response.reasoning_text.delta` | 分别映射摘要和详细推理增量 |
| 对应 `.done` / `response.output_item.done` | 用完整快照补齐缺失后缀，避免与增量重复追加 |
| `response.completed` | 校验完整 output，补齐快照、提交用量并立即关闭连接 |
| `response.incomplete` | `max_output_tokens` 或 `content_filter` 保留已有正文和原生结束原因 |
| `response.failed` / `error` | 抛出安全分类异常，不把 HTTP 200 当作生成成功 |
| EOF 且没有响应终态 | 报告断流；已发出的正文由现有业务流程保留 |
| 用户停止 | 取消 OkHttp 调用，关闭等待中的 socket 并释放临时请求文件 |

终态快照必须与已收到的文本前缀一致；正文被改写或从最终输出消失时报告异常。只有推理而没有正文仍是空回复，耗尽输出额度时保留增大预算所需的错误信息。

Token 用量映射如下，缓存和推理均为输入或输出总数的组成部分，不重复相加：

| Responses | RPClient |
| --- | --- |
| `usage.input_tokens` | `promptTokens` |
| `usage.output_tokens` | `completionTokens` |
| `usage.total_tokens` | `totalTokens` |
| `usage.input_tokens_details.cached_tokens` | `cachedPromptTokens` |
| `usage.output_tokens_details.reasoning_tokens` | `reasoningTokens` |

GPT-6 的本地预算使用带安全预留的代理估算，不宣称本地分词结果与服务端完全一致。启用服务端用量统计时以原生 usage 为准。

## 生命周期与日志

图片先由共享图片运行时准备，通过随机占位符登记，最终 Base64 流式写入有大小上限的临时请求文件。非流式、流式、请求构建失败、协议失败和取消均释放该文件。保存的图片资源不会随取消请求删除。

请求日志遵循已有调试开关和清理策略。图片 Base64 不写入日志；流式日志有长度上限。普通错误提示仅使用安全错误分类，不展示服务端原始私密请求或错误文本。

## 验证

本次验证通过 302 项 JVM 测试、Debug APK 构建及下述 3 项 Android 集成测试。取消 socket 时曾暴露 IOException 覆盖取消语义的问题，已在共享网络 IO 边界修复并通过回归。

- JVM 测试覆盖原生字段、图文顺序、中途 system、assistant phase、推理与采样冲突、高级 JSON 保护字段及 Prompt 预算路径一致性。
- 流测试覆盖多行 SSE、增量与快照去重、推理与拒绝、正文空白、commentary、用量、截断、服务端错误和异常断流。
- Android 集成测试覆盖全部协议 × 两种传输 × user/assistant × 文本/纯图/混排，共 48 组实际请求编码。
- 设备回环 HTTP 测试覆盖原生协议收到终态或取消时主动关闭空闲 socket、清除临时图片请求文件并保留用户图片。
- 无需外网服务或真实 API Key 的测试验证请求字节和协议行为；真实 OpenAI 账号的鉴权、配额与模型可用性不属于这些合成响应测试的验证结果。

常用命令：

```sh
./gradlew --offline --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew --offline --no-daemon --console=plain :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=me.kafuuneko.rpclient.libs.llm.adapter.MultimodalImageIntegrationTest
```
