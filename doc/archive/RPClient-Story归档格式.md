# RPClient Story 归档格式

RPClient 使用 `.rpstory.json` 交换故事正文、分卷章节结构与 Story 级写作配置。文件采用 UTF-8 JSON，当前主版本为 `2`。

```json
{
  "format": "rpclient_story",
  "version": 2,
  "story": {
    "title": "示例故事",
    "memory": "长期事实与大纲",
    "authorNote": "本轮写作方向",
    "summary": "当前剧情摘要",
    "includeUserPersona": false,
    "ungroupedChapters": [
      {
        "title": "序章",
        "content": "未归入分卷的正文"
      }
    ],
    "volumes": [
      {
        "title": "第一卷",
        "chapters": [
          {
            "title": "第一章",
            "content": "章节正文"
          }
        ]
      }
    ]
  },
  "characterHints": [
    {
      "name": "Alice",
      "fingerprint": "sha256",
      "activationMode": "auto"
    }
  ],
  "lorebookHints": [
    {
      "lorebookName": "Old City",
      "entryName": "Station",
      "fingerprint": "sha256"
    }
  ]
}
```

## 正文结构

`ungroupedChapters` 保存未分卷章节，`volumes` 保存分卷及卷内章节。数组顺序就是导入后的展示和导出顺序：先处理未分卷章节，再按 `volumes` 顺序处理各卷及卷内章节。空分卷允许存在，但整份归档至少必须包含一个章节。

章节和分卷标题必须是非空字符串，章节正文可以为空。单份归档最多保存 1,000 个分卷和 10,000 个章节；角色与世界书提示分别最多保存 10,000 条。URI 导入文件还受应用的 16 MiB 总大小限制。

归档不会保存本地 Room 主键、排序数值、Provider 配置、API Key、Prompt Inspector 快照、正文 revision 或世界书时序状态。导入时根据数组顺序重新生成本地排序值。

## V1 兼容

编码器只输出 V2。读取 V1 归档时，`story.content` 会转换为一个标题为“正文”的未分卷章节，其余 Story 级设置和引用提示保持不变。未知主版本会被拒绝。

纯 TXT 或 Markdown 文件不会根据标题自动拆章，而是转换为一个标题为“正文”的未分卷章节，避免普通正文中的标题样式被误判为结构。

## 文本导出

Markdown 导出按以下层级写入：

- `# 故事标题`
- 未分卷章节使用 `## 章节标题`
- 分卷使用 `## 分卷标题`
- 卷内章节使用 `### 章节标题`

TXT 导出使用不带 Markdown 标记的可读标题和空行分隔。两种格式都按归档结构顺序逐章写出，不在内存中额外拼接整本小说。

## 引用匹配与事务

导入时先完成格式和大小校验，不写数据库；用户确认标题后才在单个事务中创建 Story、分卷、章节及引用关系。角色和世界书提示优先按内容指纹唯一匹配，再按名称唯一匹配。存在多个候选时不会自动关联，避免跨设备自增 ID 或重名资源造成错误绑定。

导入错误只显示通用原因，不回显完整正文、真实文件路径或堆栈。
