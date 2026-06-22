package net.rwhps.server.plugin

import net.rwhps.server.data.bean.internal.BeanPluginInfo
import net.rwhps.server.data.global.Data
import net.rwhps.server.struct.map.OrderedMap
import net.rwhps.server.util.str.StringUtils
import net.rwhps.server.util.algorithms.Base64
import net.rwhps.server.util.log.Log
import org.junit.jupiter.api.Test

/**
 * 建议使用 jslib 项目开发, 裸写 mjs 是受罪
 *
 * 什么傻逼玩意, 测试都能写半天
 *
 * @date 2023/6/21 11:00 想骂人
 * @date 2023/6/21 13:09 更想骂人
 *
 * @author Dr (dr@der.kim)
 */
class JavaScriptPluginTest {
    init {
        Log.set("ALL")
    }

    @Test
    fun loadJavaScriptPlugin() {
        val scriptPluginGlobalContext = JavaScriptPluginGlobalContext()

        /**
         * 基础测试
         * 测试内容 :
         * 调用其他文件, 加载其他文件, 操作 Java 方法
         */
        val baseTest = OrderedMap<String, ByteArray>().apply {
            put(
                    "main.mjs", StringUtils.bytes(
                    """
                import {Test} from './test.mjs'
                import {Plugin} from 'java://net.rwhps.server.plugin'
                import {GetVersion} from 'java://net.rwhps.server.plugin'
                import {Log} from 'java://net.rwhps.server.util.log'
                
                export default new (Java.extend(Plugin, {
                        onEnable: function() {
                            Log.debug("Hi onEnable Test-1")
                            Log.debug(new Test().square(5))
                            Log.debug(new GetVersion("1.0.0-M1").toString())
                        },
                        init: function() {
                            Log.debug("oneClass")
                        }
                    }))()
            """.trimIndent(), Data.UTF_8
            )
            )

            put(
                    "test.mjs", StringUtils.bytes(
                    """
                export class Test {
                    square(x) {
                        return x * x;
                    }
                }
            """.trimIndent(), Data.UTF_8
            )
            )
        }
        scriptPluginGlobalContext.addESMPlugin(
                BeanPluginInfo(
                        name = "JsScriptBaseTest",
                        internalName = "JsScriptBaseTest",
                        author = "Dr",
                        main = "main.mjs"
                ), baseTest
        )


        /**
         * 扩展测试
         * 测试内容 :
         * 其他文件的解析/导入, WASM 的加载
         */
        val extendedTest = OrderedMap<String, ByteArray>().apply {
            put(
                    "main.mjs", StringUtils.bytes(
                    """
                import a from './index.js/?text'
                import b from './index.json/?json'
                import {Plugin} from 'java://net.rwhps.server.plugin'
                import bytes from './square.wasm/?bytes'
                import was from './square.wasm/?wasm'
                
                const wasmModule = new WebAssembly.Module(bytes);
                const wasmInstance = new WebAssembly.Instance(wasmModule);
                // 获取导出的函数
                const helloFunc = wasmInstance.exports.hello;
                
                export default new (Java.extend(Plugin, {
                        onEnable: function() {
                            console.log(a)
                            console.log(JSON.stringify(b))
                            // 调用函数
                            const input = 5; // 输入参数
                            const result = helloFunc(input);
                            
                            // 打印结果
                            console.log(result);
                            console.log(was.exports.hello(4));
                        }
                    }))()
            """.trimIndent(), Data.UTF_8
            )
            )

            put(
                    "index.json", StringUtils.bytes(
                    """
                {
                    "hello": "world"
                }
            """.trimIndent()
            )
            )

            put("square.wasm", Base64.decode("AGFzbQEAAAABhoCAgAABYAF/AX8DgoCAgAABAASEgICAAAFwAAAFg4CAgAABAAEGgYCAgAAAB5KAgIAAAgZtZW1vcnkCAAVoZWxsbwAACo2AgIAAAYeAgIAAACAAIABsCw=="))
        }
        scriptPluginGlobalContext.addESMPlugin(
                BeanPluginInfo(
                        name = "JsScriptExtendedTest",
                        internalName = "JsScriptExtendedTest",
                        author = "Dr",
                        main = "main.mjs"
                ), extendedTest
        )

        // 运行
        scriptPluginGlobalContext.loadESMPlugins().eachAll {
            it.main.onEnable()
            it.main.init()
        }
    }

    @Test
    fun fetchWeb() {
        val scriptPluginGlobalContext = JavaScriptPluginGlobalContext()

        val plugin = OrderedMap<String, ByteArray>().apply {
            put(
                    "index.js", StringUtils.bytes(
                    """
                const a = RwHps.readRamText('https://www.baidu.com')
                
                export default new (Java.extend(Java.type('net.rwhps.server.plugin.Plugin'), {
                    onEnable() {
                        console.log(a)
                    }
                }))()
            """.trimIndent()
            )
            )
        }

        scriptPluginGlobalContext.addESMPlugin(
                BeanPluginInfo(
                        name = "a", author = "zerodegress", main = "index.js"
                ), plugin
        )

        scriptPluginGlobalContext.loadESMPlugins().eachAll {
            it.main.onEnable()
            it.main.init()
        }
    }

    @Test
    fun urlImport() {
        val scriptPluginGlobalContext = JavaScriptPluginGlobalContext()

        val plugin1 = OrderedMap<String, ByteArray>().apply {
            put(
                    "index.js", StringUtils.bytes(
                    """
                export const a = 10
                export default new (Java.extend(Java.type('net.rwhps.server.plugin.Plugin'), {}))()
            """.trimIndent()
            )
            )
        }

        val plugin2 = OrderedMap<String, ByteArray>().apply {
            put(
                    "index.js", StringUtils.bytes(
                    """
                import { a } from "plugin://a"
                //import { bbb } from "plugin://c"
                import { bbb } from "ram:///plugins/c/index.js"
                export default new (Java.extend(Java.type('net.rwhps.server.plugin.Plugin'), {
                    onEnable() {
                        console.log(a)
                        console.log(bbb)
                        //console.log(c)
                    }
                }))()
            """.trimIndent()
            )
            )
        }

        val plugin3 = OrderedMap<String, ByteArray>().apply {
            put(
                    "index.js", StringUtils.bytes(
                    """
                export const bbb = 100
                export default new (Java.extend(Java.type('net.rwhps.server.plugin.Plugin'), {}))()
            """.trimIndent()
            )
            )
        }

        // 这个是模块化, 可以使用import/export
        scriptPluginGlobalContext.addESMPlugin(
                BeanPluginInfo(
                        name = "aaaaaa",
                        internalName = "a",
                        author = "zerodegress",
                        main = "index.js"
                ), plugin1
        )
        scriptPluginGlobalContext.addESMPlugin(
                BeanPluginInfo(
                        name = "b",
                        internalName = "b",
                        author = "zerodegress",
                        main = "index.js"
                ), plugin2
        )
        scriptPluginGlobalContext.addESMPlugin(
                BeanPluginInfo(
                        name = "cxxxx",
                        internalName = "c",
                        author = "zerodegress",
                        main = "index.js"
                ), plugin3
        )
        scriptPluginGlobalContext.loadESMPlugins().eachAll {
            it.main.onEnable()
            it.main.init()
        }
    }
}