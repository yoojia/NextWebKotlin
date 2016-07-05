package com.github.yoojia.web.supports

import com.github.yoojia.web.util.concat
import java.lang.reflect.Method

/**
 * 为模块类中@GET/POST/PUT/DELETE等方法创建的请求处理器。
 * 对应每个Java method生成一个RequestHandler；
 * @author Yoojia Chen (yoojiachen@gmail.com)
 * @since 2.0
 */
class RequestHandler(
        val root: String,
        val invoker: JavaMethodInvoker,
        val comparator: Comparator,
        val priority: Int = getRequestPriority(comparator)) {

    val javaMethod: Method by lazy {
        invoker.method
    }

    override fun toString(): String {
        return "{invoker: $invoker, comparator: $comparator, priority: $priority}"
    }

    companion object {

        internal fun create(root: String, moduleType: Class<*>, method: Method, annotationType: Class<out Annotation>): RequestHandler {
            val annotation = method.getAnnotation(annotationType)
            val arg = when(annotation) {
                is GET -> Pair("GET", annotation.value)
                is POST -> Pair("POST", annotation.value)
                is PUT -> Pair("PUT", annotation.value)
                is DELETE -> Pair("DELETE", annotation.value)
                is ALL -> Pair("ALL", annotation.value)
                else -> throw IllegalArgumentException("Unexpected annotation <$annotation> in method: $method")
            }
            return RequestHandler(root, JavaMethodInvoker(moduleType, method),
                    Comparator.createDefine(arg.first/*method*/, concat(root, arg.second/*path*/)))
        }
    }
}