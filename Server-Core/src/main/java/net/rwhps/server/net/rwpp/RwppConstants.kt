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
 * RWJS 客户端 mod 传输协议常量。
 * PREREGISTER 首字段仍使用 [PACKAGE_NAME]（`io.github.rwpp`）作为线上格式前缀，与 RWJS 客户端一致。
 */
object RwppConstants {
    /** PREREGISTER_INFO 首字段前缀，RWJS 客户端据此识别 mod 传输能力。 */
    const val PACKAGE_NAME = "io.github.rwpp"

    /** 与 RWJS [protocolVersion] 对齐，不匹配时客户端会断连。 */
    const val DEFAULT_PROTOCOL_VERSION = 4

    const val MOD_DOWNLOAD_REQUEST = 500
    const val MOD_RELOAD_FINISH = 502
    const val MOD_CHUNK_ACK = 503
    const val DOWNLOAD_MOD_PACK = 510
    const val DOWNLOAD_MOD_CHUNK = 511

    const val CHUNK_SIZE = 64 * 1024
}
