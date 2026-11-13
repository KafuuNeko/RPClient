package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.room.entity.UserPersona

/** 只投影下拉菜单需要的身份，不加载头像或描述正文。 */
data class PersonaOption(val id: Long, val name: String)

/** 页面草稿中的人设选择，独立于全局默认项。 */
data class PersonaSelection(
    val options: List<PersonaOption> = emptyList(),
    val selectedId: Long = UserPersona.NONE,
    val followDefaultName: String? = null,
) {
    /** 已选身份的原始名称，禁用项没有对应人设行。 */
    val selectedName: String? get() = if (selectedId == UserPersona.FOLLOW_DEFAULT) followDefaultName else options.firstOrNull { it.id == selectedId }?.name
}
