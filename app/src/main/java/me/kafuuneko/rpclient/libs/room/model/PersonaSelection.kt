package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.room.entity.UserPersona

/** 只投影下拉菜单需要的身份，不加载头像或描述正文。 */
data class PersonaOption(val id: Long, val name: String)

/** 页面草稿中的人设选择，独立于全局默认项。 */
data class PersonaSelection(
    val options: List<PersonaOption> = emptyList(),
    val selectedId: Long = NO_EXPLICIT_PERSONA,
    val followDefaultName: String? = null,
) {
    companion object {
        /** 页面未绑定特定人设时的选项 ID，由场景决定跟随默认或禁用。 */
        const val NO_EXPLICIT_PERSONA = UserPersona.NONE
    }

    /** 已选身份的原始名称，禁用项没有对应人设行。 */
    val selectedName: String? get() = if (selectedId == UserPersona.FOLLOW_DEFAULT) followDefaultName else options.firstOrNull { it.id == selectedId }?.name
}
