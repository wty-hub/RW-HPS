/*
 * Copyright 2020-2024 Dr (dr@der.kim) and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/deng-rui/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.net.rwpp

/**
 * RWJS [RoomOption] 的 RW-HPS 侧镜像；TOML 字段名须与 RWJS 客户端 kotlinx.serialization 序列化结果一致。
 */
data class RwppRoomOption(
    val canTransferMod: Boolean = false,
    val allModsSize: Int = 0,
    val protocolVersion: Int = RwppConstants.DEFAULT_PROTOCOL_VERSION,
) {
    /** 编码为 RWJS 客户端可解析的 TOML 片段。 */
    fun encodeToml(): String = buildString {
        append("canTransferMod = ").append(canTransferMod).append('\n')
        append("allModsSize = ").append(allModsSize).append('\n')
        append("protocolVersion = ").append(protocolVersion)
    }

    fun preregisterPrefix(): String = RwppConstants.PACKAGE_NAME + encodeToml()
}
