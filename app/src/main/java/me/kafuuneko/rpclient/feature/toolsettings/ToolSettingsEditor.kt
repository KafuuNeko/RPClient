package me.kafuuneko.rpclient.feature.toolsettings

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.feature.toolsettings.presentation.BuiltinDefaultDraft
import me.kafuuneko.rpclient.feature.toolsettings.presentation.BuiltinParamDraft
import me.kafuuneko.rpclient.feature.toolsettings.presentation.BuiltinToolListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.HttpBindingDraft
import me.kafuuneko.rpclient.feature.toolsettings.presentation.HttpToolListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.KeyValueDraft
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolConnectionListItem
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsContentState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsDialogState
import me.kafuuneko.rpclient.feature.toolsettings.presentation.ToolSettingsEditorMode
import me.kafuuneko.rpclient.libs.agent.connection.BuiltinToolOverrideCodec
import me.kafuuneko.rpclient.libs.agent.connection.BuiltinToolOverrideData
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolBinding
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolConfigCodec
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolConnection
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolKind
import me.kafuuneko.rpclient.libs.agent.connection.HttpToolMapping
import me.kafuuneko.rpclient.libs.agent.connection.ToolBindingTarget
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigCodec
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigValidationException
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigurationStatus
import me.kafuuneko.rpclient.libs.agent.connection.ToolCredentialRepository
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolCatalog
import me.kafuuneko.rpclient.libs.room.entity.ToolConnection
import me.kafuuneko.rpclient.libs.room.entity.ToolDefinition
import me.kafuuneko.rpclient.libs.room.repository.ToolConfigRepository


/**
 * 工具设置的配置编辑与展示投影服务。
 * - ViewModel 仅协调用户动作，本服务不持有页面状态。
 * - 保存经权威仓库单次校验，预览复用运行工厂的冻结解析器。
 */
class ToolSettingsEditor(
    private val mConfigurations: ToolConfigRepository,
    private val mCredentials: ToolCredentialRepository,
    private val mCatalog: ToolCatalog,
    private val mToolStatus: ToolConfigurationStatus,
) {
    /** @return 指定工具的原始值、当前覆盖及生效预览；未知 ID 返回 null。 */
    suspend fun builtin(id: String): ToolSettingsDialogState.BuiltinEditor? {
        val registration = mCatalog.find(id) ?: return null
        val definition = registration.specification.definition()
        val existingRaw = mConfigurations.overrides().firstOrNull { it.toolId == id }
            ?.configJson
        val override = runCatching { BuiltinToolOverrideCodec.decode(existingRaw) }
            .getOrDefault(BuiltinToolOverrideData())
        val defaultLabelRes = registration.labelRes
        // 从权威 Schema 派生真实参数列表与允许调整的分页默认值键。
        val params = definition.parameters.properties.map { (paramName, schema) ->
            BuiltinParamDraft(
                name = paramName,
                typeLabel = schema.type.name.lowercase(),
                required = paramName in definition.parameters.required,
                defaultDescription = schema.description,
                overrideDescription = override.parameterDescriptions[paramName].orEmpty(),
            )
        }
        val editableDefaults = registration.specification.editableDefaults.map { spec ->
            val propertyType = definition.parameters.properties[spec.key]?.type
            BuiltinDefaultDraft(
                key = spec.key,
                labelRes = spec.labelRes,
                value = override.defaults[spec.key]?.let { formatDefault(it, propertyType) }
                    .orEmpty(),
                originalValue = formatDefault(spec.defaultValue, propertyType),
                numeric = propertyType in setOf(ToolSchema.Type.Integer, ToolSchema.Type.Number),
            )
        }
        val editor = ToolSettingsDialogState.BuiltinEditor(
            toolId = definition.toolId,
            defaultLabelRes = defaultLabelRes,
            originalDescription = definition.description,
            displayName = override.displayName,
            description = override.description,
            usageHint = override.usageHint,
            parameters = params,
            editableDefaults = editableDefaults,
            effectiveDescription = mCatalog.resolve(id, existingRaw).definition.description,
            customized = existingRaw != null,
        )
        return editor
    }

    /** 更新生效预览，草稿尚未通过校验时清空预览以免展示过期结果。 */
    fun preview(draft: ToolSettingsDialogState.BuiltinEditor): ToolSettingsDialogState.BuiltinEditor {
        val effective =
            runCatching { mCatalog.resolve(draft.toolId, encodeBuiltin(draft)) }.getOrNull()
        return draft.copy(effectiveDescription = effective?.definition?.description.orEmpty())
    }

    /** 保存结构化连接表单至权威配置仓库。 */
    suspend fun saveConnectionDraft(draft: ToolSettingsDialogState.ConnectionEditor) {
        val timeoutSeconds = draft.timeoutSeconds.trim().toLongOrNull()
            ?: throw ToolConfigValidationException(ToolErrorCode.INVALID_LIMITS, "Invalid timeout")
        // 忽略完全空白的键值对行；填写了键或值其中一项但另一项缺键时明确报错。
        val queryMap = linkedMapOf<String, String>()
        draft.queryParams.forEach { row ->
            val key = row.key.trim()
            if (key.isEmpty() && row.value.isEmpty()) return@forEach
            if (key.isEmpty()) throw ToolConfigValidationException(
                ToolErrorCode.INVALID_MAPPING,
                "Empty query key"
            )
            queryMap[key] = row.value
        }
        val headerMap = linkedMapOf<String, String>()
        draft.headers.forEach { row ->
            val key = row.key.trim()
            if (key.isEmpty() && row.value.isEmpty()) return@forEach
            if (key.isEmpty()) throw ToolConfigValidationException(
                ToolErrorCode.INVALID_HEADERS,
                "Empty header name"
            )
            headerMap[key] = row.value
        }
        val config = HttpToolConnection(
            url = draft.url.trim(),
            method = draft.method,
            query = queryMap,
            headers = headerMap,
            authMode = draft.authMode,
            authHeader = draft.authHeader.trim().ifEmpty { defaultAuthFieldName(draft.authMode) },
            credentialRef = draft.credentialRef,
            timeoutMillis = timeoutSeconds * 1000L,
            maxResponseBytes = draft.maxResponseBytes,
            allowPrivateAddresses = draft.allowPrivateAddresses,
        )
        mConfigurations.save(
            ToolConnection(
                id = draft.id,
                name = draft.name.trim(),
                configJson = HttpToolConfigCodec.encode(config),
                enabled = draft.enabled,
            ),
        )
    }

    /** 保存结构化 HTTP 工具表单至权威配置仓库。 */
    suspend fun saveToolDraft(draft: ToolSettingsDialogState.ToolEditor) {
        val mappingJson = if (draft.kind == HttpToolKind.ReadUrl) {
            "{}"
        } else {
            val bodyObject = ToolConfigCodec.parse(draft.bodyJson.ifBlank { "{}" })
            // 过滤空白绑定与字段投影，组装结构化映射再交由编解码器强校验。
            val bindings = draft.bindings.mapNotNull { row ->
                val argument = row.argument.trim()
                val path = row.path.trim()
                if (argument.isEmpty() && path.isEmpty()) return@mapNotNull null
                val normalizedArg = if (argument.startsWith('/')) argument else "/$argument"
                val normalizedPath =
                    if (row.target == ToolBindingTarget.Body && path.isNotEmpty() && !path.startsWith(
                            '/'
                        )
                    ) {
                        "/$path"
                    } else {
                        path
                    }
                HttpToolBinding(
                    argument = normalizedArg,
                    target = row.target,
                    path = normalizedPath
                )
            }
            val fields = linkedMapOf<String, String>()
            draft.fields.forEach { row ->
                val key = row.key.trim()
                val path = row.value.trim()
                if (key.isEmpty() && path.isEmpty()) return@forEach
                if (key.isEmpty()) throw ToolConfigValidationException(
                    ToolErrorCode.INVALID_MAPPING,
                    "Empty field name"
                )
                fields[key] = if (path.isEmpty() || path.startsWith('/')) path else "/$path"
            }
            val responsePath = draft.responsePath.trim().let { raw ->
                if (raw.isEmpty() || raw.startsWith('/')) raw else "/$raw"
            }
            val errorPath = draft.errorPath.trim().takeIf { it.isNotEmpty() }?.let { raw ->
                if (raw.startsWith('/')) raw else "/$raw"
            }
            val nextCursorPath =
                draft.nextCursorPath.trim().takeIf { it.isNotEmpty() }?.let { raw ->
                    if (raw.startsWith('/')) raw else "/$raw"
                }
            HttpToolConfigCodec.encode(
                HttpToolMapping(
                    body = bodyObject,
                    bindings = bindings,
                    responsePath = responsePath,
                    errorPath = errorPath,
                    nextCursorPath = nextCursorPath,
                    fields = fields,
                ),
            )
        }
        mConfigurations.save(
            ToolDefinition(
                id = draft.id,
                name = draft.name.trim(),
                description = draft.description.trim(),
                parametersJson = draft.parametersJson,
                mappingJson = mappingJson,
                connectionId = if (draft.kind == HttpToolKind.ReadUrl) null else draft.connectionId,
                kind = draft.kind,
                enabled = draft.enabled,
            ),
        )
    }

    /** 保存内置工具稀疏覆写表单；全部清空时自动恢复内置默认配置。 */
    suspend fun saveBuiltinDraft(draft: ToolSettingsDialogState.BuiltinEditor) {
        mConfigurations.saveOverride(draft.toolId, encodeBuiltin(draft))
    }

    /** @return 未产生 IO 的稀疏覆写 JSON，编辑预览与保存共用。 */
    private fun encodeBuiltin(draft: ToolSettingsDialogState.BuiltinEditor): String {
        val properties =
            mCatalog.find(draft.toolId)?.specification?.prompt?.parameters?.properties.orEmpty()
        // 按目标参数类型解析可编辑默认值，字符串字段直接使用原文而非要求用户输入 JSON 引号。
        val defaults = draft.editableDefaults.filter { it.value.isNotBlank() }.associate { field ->
            val parsed = if (properties[field.key]?.type == ToolSchema.Type.String) {
                JsonPrimitive(field.value.trim())
            } else {
                try {
                    AgentJson.parse(field.value)
                } catch (_: Exception) {
                    throw ToolConfigValidationException(
                        ToolErrorCode.INVALID_OVERRIDE,
                        "Invalid default value"
                    )
                }
            }
            field.key to parsed
        }
        // 剥离空白覆写项并序列化为紧凑稀疏 JSON，与运行期解析共享编码契约。
        val paramDescriptions = draft.parameters
            .associate { it.name to it.overrideDescription.trim() }
            .filterValues { it.isNotEmpty() }
        return BuiltinToolOverrideCodec.encode(
            BuiltinToolOverrideData(
                displayName = draft.displayName.trim(),
                description = draft.description.trim(),
                usageHint = draft.usageHint.trim(),
                parameterDescriptions = paramDescriptions,
                defaults = defaults,
            ),
        )
    }

    /** 将默认参数 JSON 值格式化为表单展示文本，字符串类型隐藏外层引号。 */
    private fun formatDefault(value: JsonElement, type: ToolSchema.Type?): String =
        if (type == ToolSchema.Type.String && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            value.asString
        } else {
            value.toString()
        }

    /** 从数据库重新构建连接、HTTP 工具与内置工具的非敏感摘要列表。 */
    suspend fun content(): ToolSettingsContentState {
        val rawConnections = mConfigurations.connections()
        val rawTools = mConfigurations.tools()
        val boundCounts = rawTools.mapNotNull { it.connectionId }.groupingBy { it }.eachCount()
        // 逐个检查连接 URL 与本机凭据可用性，不将密钥正文放入 UI 模型。
        val connectionItems = rawConnections.map { connection ->
            val readiness = mToolStatus.connectionState(connection)
            val config = readiness.configuration ?: HttpToolConnection("")
            val hasCredential = readiness.hasCredential
            val missing = readiness.needsConfiguration
            ToolConnectionListItem(
                id = connection.id,
                name = connection.name,
                url = config.url,
                method = config.method,
                authMode = config.authMode,
                authHeader = config.authHeader,
                hasCredential = hasCredential,
                allowPrivateAddresses = config.allowPrivateAddresses,
                enabled = connection.enabled,
                needsConfiguration = missing,
                boundToolCount = boundCounts[connection.id] ?: 0,
            )
        }
        val connectionMap = connectionItems.associateBy { it.id }
        val toolItems = rawTools.map { tool ->
            val bound = tool.connectionId?.let(connectionMap::get)
            val missing = mToolStatus.needsConfiguration(
                tool.kind,
                bound?.enabled == true,
                bound?.needsConfiguration ?: true
            )
            HttpToolListItem(
                id = tool.id,
                name = tool.name,
                description = tool.description,
                kind = tool.kind,
                enabled = tool.enabled,
                connectionName = bound?.name,
                connectionMethod = bound?.method,
                needsConfiguration = missing,
            )
        }
        // 内置工具按统一顺序展示本地化默认名、自定义名及简要描述。
        val overrides = mConfigurations.overrides().associate { it.toolId to it.configJson }
        val builtinItems = mCatalog.registrations.map { registration ->
            val id = registration.specification.toolId
            val group = registration.specification.group
            val overrideData = overrides[id]?.let {
                runCatching { BuiltinToolOverrideCodec.decode(it) }.getOrNull()
            }
            val effectiveDescription = mCatalog.resolve(id, overrides[id]).definition.description
            BuiltinToolListItem(
                id = id,
                defaultLabelRes = registration.labelRes,
                customDisplayName = overrideData?.displayName?.takeIf { it.isNotBlank() },
                group = group,
                descriptionPreview = effectiveDescription,
                customized = id in overrides,
            )
        }
        return ToolSettingsContentState(
            tools = toolItems,
            connections = connectionItems,
            builtins = builtinItems
        )
    }

    /** 将连接实体解析为结构化表单草稿。 */
    suspend fun buildConnectionEditor(
        value: ToolConnection,
        mode: ToolSettingsEditorMode,
    ): ToolSettingsDialogState.ConnectionEditor {
        // 解析连接配置并仅探测凭据存在性，完整保留原 maxResponseBytes 不做静默取整。
        val config = HttpToolConfigCodec.connection(value.configJson)
        val hasCredential = config.credentialRef?.let { mCredentials.read(it) != null } == true
        return ToolSettingsDialogState.ConnectionEditor(
            id = value.id,
            mode = mode,
            name = value.name,
            url = config.url,
            method = config.method,
            authMode = config.authMode,
            authHeader = config.authHeader,
            credentialRef = config.credentialRef,
            hasCredential = hasCredential,
            timeoutSeconds = (config.timeoutMillis / 1000L).toString(),
            maxResponseBytes = config.maxResponseBytes,
            allowPrivateAddresses = config.allowPrivateAddresses,
            queryParams = config.query.map { (k, v) -> KeyValueDraft(k, v) },
            headers = config.headers.map { (k, v) -> KeyValueDraft(k, v) },
            enabled = value.enabled,
        )
    }

    /** 将工具实体解析为结构化表单草稿。 */
    fun buildToolEditor(
        value: ToolDefinition,
        mode: ToolSettingsEditorMode,
    ): ToolSettingsDialogState.ToolEditor {
        // 格式化 Schema 与静态请求体供独立代码编辑器使用，并派生参数摘要标签。
        val prettyParams = runCatching {
            ToolConfigCodec.pretty(ToolConfigCodec.parse(value.parametersJson))
        }.getOrDefault(value.parametersJson)
        val mapping = runCatching { HttpToolConfigCodec.mapping(value.mappingJson) }
            .getOrDefault(HttpToolMapping())
        val prettyBody = runCatching { ToolConfigCodec.pretty(mapping.body) }.getOrDefault("{}")
        return ToolSettingsDialogState.ToolEditor(
            id = value.id,
            mode = mode,
            kind = value.kind,
            name = value.name,
            description = value.description,
            connectionId = value.connectionId,
            parametersJson = prettyParams,
            parameterSummary = summarizeSchema(prettyParams),
            responsePath = mapping.responsePath,
            errorPath = mapping.errorPath.orEmpty(),
            nextCursorPath = mapping.nextCursorPath.orEmpty(),
            bodyJson = prettyBody,
            bindings = mapping.bindings.map { HttpBindingDraft(it.argument, it.target, it.path) },
            fields = mapping.fields.map { (k, v) -> KeyValueDraft(k, v) },
            enabled = value.enabled,
        )
    }

}
