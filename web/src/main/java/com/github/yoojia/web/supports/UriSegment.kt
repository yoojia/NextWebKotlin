package com.github.yoojia.web.supports

/**
 * @author Yoojia Chen (yoojiachen@gmail.com)
 * @since 2.a.2
 */
class UriSegment(segment: String, absoluteType: Boolean = false) {

    val dynamic: Boolean
    val wildcard: Boolean
    val type: ValueType
    val name: String

    init {
        val starts = if(segment.startsWith('{')) 1 else 0
        val ends = if(segment.endsWith('}')) 1 else 0
        if(starts.xor(ends) == 1) {
            throw IllegalArgumentException("Invalid uri segment: $segment")
        }
        dynamic = segment.length >= 3/*{a}*/&& starts.and(ends) == 1

        wildcard = !dynamic && "*".equals(segment)

        // {user-id} -> user-id
        val unwrap = if(dynamic) segment.substring(1, segment.length-1) else segment

        if(!dynamic) {
            name = segment
            type = ValueType.get(name)
        }else when{
            unwrap.startsWith("int:") -> {
                type = ValueType.Int
                name = unwrap.substring(4)
            }
            unwrap.startsWith("float:") -> {
                type = ValueType.Float
                name = unwrap.substring(6)
            }
            unwrap.startsWith("string:") -> {
                type = ValueType.String
                name = unwrap.substring(7)
            }
            else -> {
                type = if(absoluteType) ValueType.String else ValueType.Any
                name = unwrap
            }
        }
    }

    companion object {

        /**
         * 在UriSegment资源长度相同的情况下，判断它们是否匹配；
         * - 定义为动态参数：比较它们的类型是否相同，忽略资源名；定义为字符串类型时，可以匹配任意请求资源类型；
         * - 定义为静态字段：比较资源名是否相同（大小写完全相同）；
         */
        private fun match(requests: List<UriSegment>, defines: List<UriSegment>): Boolean{
            for(i in requests.indices) {
                val define = defines[i]
                val request = requests[i]
                val match: Boolean
                if(define.dynamic) {
                    match = define.type.match(request.type)
                }else{
                    match = define.name.equals(request.name, ignoreCase = false)
                }
                if(!match) return false
            }
            return true
        }

        /**
         * 客户端请求的UriSegments与定义的UriSegments是否匹配。
         */
        fun isRequestMatchDefine(request: List<UriSegment>, define: List<UriSegment>): Boolean {
            if(define.last().wildcard) {
                val defineIndex = define.size - 1
                if(request.size < defineIndex) {
                    return false
                }else{
                    return match(request.subList(0, defineIndex), define.subList(0, defineIndex))
                }
            }else{
                return request.size == define.size && match(request, define)
            }
        }
    }

    enum class ValueType {

        Any,
        String,
        Float,
        Int;

        fun match(other: ValueType): Boolean {
            if(Any.equals(this) || Any.equals(other)) {
                return true
            }else{
                return this.equals(other)
            }
        }

        companion object {

            fun get(resource: kotlin.String): ValueType {
                // resource is a shot string !!!
                // Double: float, double is digits and '.'
                // Long: int, long is all digits
                // String: string, otherwise
                var dotCount: kotlin.Int = 0
                var digitCount: kotlin.Int = 0
                val len = resource.length
                resource.forEachIndexed { i, char ->
                    if('.'.equals(char)) {
                        dotCount += 1
                        if(dotCount > 1 /* 12..6 */|| i == 0/* .5 */ || i == (len - 1)/* 124. */) {
                            dotCount = -1
                            return@forEachIndexed
                        }
                    }else if(Character.isDigit(char) || (i == 0 && '-'.equals(char))) {
                        digitCount += 1
                    }
                }
                when{
                    dotCount == 0 && digitCount == len -> return Int
                    dotCount == 1 && digitCount == (len - 1) -> return Float
                    else -> return String
                }
            }
        }
    }
}