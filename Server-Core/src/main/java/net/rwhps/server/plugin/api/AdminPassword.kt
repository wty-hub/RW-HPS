/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.api

/**
 * 管理员密码校验接口，由 Password 插件实现并注册到 [AdminPassword]。
 */
interface AdminPasswordVerifier {
    /** 是否已设置管理员密码 */
    fun isConfigured(): Boolean

    /** 校验明文密码是否与已存储的哈希匹配 */
    fun verify(password: String): Boolean
}

/**
 * 跨插件共享的管理员密码入口。
 *
 * 依赖 Password 插件的其他模块应通过此类校验密码，并在 `plugin.json` 中声明 `"import": "Password"` 以保证加载顺序。
 */
object AdminPassword {
    @Volatile
    private var verifier: AdminPasswordVerifier? = null

    /** Password 插件在 [net.rwhps.server.plugin.Plugin.init] 中调用 */
    @JvmStatic
    fun register(verifier: AdminPasswordVerifier) {
        this.verifier = verifier
    }

    /** Password 插件在 [net.rwhps.server.plugin.Plugin.onDisable] 中调用 */
    @JvmStatic
    fun unregister(verifier: AdminPasswordVerifier) {
        if (this.verifier === verifier) {
            this.verifier = null
        }
    }

    /** Password 插件是否已加载并完成注册 */
    @JvmStatic
    fun isAvailable(): Boolean = verifier != null

    /** 是否已设置管理员密码 */
    @JvmStatic
    fun isConfigured(): Boolean = verifier?.isConfigured() == true

    /**
     * 校验管理员密码。
     * @return 密码正确返回 `true`；未配置、插件未加载或密码错误返回 `false`
     */
    @JvmStatic
    fun verify(password: String): Boolean {
        val v = verifier ?: return false
        if (!v.isConfigured()) {
            return false
        }
        return v.verify(password)
    }
}
