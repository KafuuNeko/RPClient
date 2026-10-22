# Portable full-data backup and restore

RPClient's **Settings → Data & System → Backup & Restore** screen writes `RPClient-Portable-Data.zip`.

## Archive layout

```text
RPClient-Portable-Data.zip
├── manifest.json
├── settings.json
├── characters/character-<id>.json
├── stories/story-<id>.rpstory.json
├── chats/conversation-<id>.jsonl
├── group-chats/group-<id>.jsonl
├── worldbooks/worldbook-<id>.json
├── regex/
│   ├── global-scripts.json
│   ├── character-scripts/character-<id>.json
│   └── character-authorizations.json
├── providers/provider-configurations.json
├── attachments/
│   ├── characters/
│   ├── chat-images/
│   └── other-files/
└── app-data/
    ├── token-usage.json
    ├── message-images.json
    ├── files-index.json
    ├── request-logs.jsonl
    ├── database-schema.json
    ├── request-log-schema.json
    ├── other-tables.jsonl
    ├── shared-prefs/*.xml
    └── private-files/
```

The archive provides feature-oriented JSON/JSONL files and embeds exact table-row records for full restoration. Characters, stories, individual chats, Group Chats, World Books, regex scripts, model providers/API keys, token usage, request logs, files/image references, otherwise-unmapped tables, preferences, and private app files are retained. Binary assets are stored in `attachments/other-files/` as canonical files, with categorized copies under character avatars and chat images. Story JSON uses the RPClient `.rpstory.json` V2 envelope; lossless RPClient-specific row data is included as an extension.

`manifest.json` lists every archive file's size and SHA-256. Restore validates archive paths, file hashes, the reconstructed SQLite integrity, and foreign-key consistency before replacing live files. The earlier version-1 JSONL snapshot format remains supported, and the original SQLite snapshot format remains restorable.

## Full restore

Restore is **full replacement**, never merge. It replaces both databases, preferences, repository attachments, and app-private files, then restarts RPClient. Save a separate copy of current data first if you may need it later.

## Security

The ZIP is not encrypted. It can contain API keys, custom headers, prompts, conversations, raw request/response logs, and user attachments. Anyone who obtains the ZIP can read its contents. Store it privately and do not publish it.
