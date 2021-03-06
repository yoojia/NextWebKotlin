package com.github.yoojia.web

import com.github.yoojia.web.lang.KeyMap
import com.github.yoojia.web.lang.StreamKit
import com.github.yoojia.web.lang.splitToArray
import com.github.yoojia.web.supports.Comparator
import java.io.InputStreamReader
import java.io.StringWriter
import java.net.URLDecoder
import java.util.*
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest

/**
 * @author Yoojia Chen (yoojiachen@gmail.com)
 * @since 2.0
 */
class Request(ctx: Context, request: HttpServletRequest){

    @JvmField val servletRequest: HttpServletRequest

    @JvmField val context: Context

    @JvmField val method: String
    @JvmField val path: String
    @JvmField val contextPath: String
    @JvmField val host: String
    @JvmField val ip: String
    @JvmField val port: Int
    @JvmField val userAgent: String
    @JvmField val contentType: String

    @JvmField val createTime: Long
    @JvmField val resources: List<String>
    @JvmField val comparator: Comparator

    // （对用户只读）动态参数的意义是：用户定义的模块处理方法(method)的动态参数，
    // 通过动态参数来限定它（当前处理方法）的生命周期只存在于当前处理方法，隔离于其它处理方法。
    private val dynamicParams = KeyMap(HashMap<String, Any>())

    // 整个请求生命周期内的请求参数：由客户端提交，以及用户实现的程序内部设置。
    private val requestParams: MutableMap<String, MutableList<String>> by lazy {
        val params: MutableMap<String, MutableList<String>> = HashMap()
        for((key, value) in request.parameterMap) {
            params.put(key, value.toMutableList())
        }
        if(request.method.toUpperCase() in PUT_DELETE) {
            readBodyStream()?.let { data ->
                params.put(BODY_DATA_NAME, mutableListOf(data))
                if(CONTENT_TYPE_FORM.equals(request.contentType, ignoreCase = true)) {
                    data.split('&').forEach { pair ->
                        val kv = pair.split('=')
                        if(kv.size != 2) throw IllegalArgumentException("Client request post invalid query string")
                        putOrNew(kv[0], URLDecoder.decode(kv[1], "UTF-8"), params)
                    }
                }
            }
        }
        /* return */params
    }

    companion object {
        private val PUT_DELETE = setOf("PUT", "DELETE")
        private val GET_POST = setOf("GET", "POST")

        @JvmField val CONTENT_TYPE_FORM = "application/x-www-form-urlencoded"
        @JvmField val CONTENT_TYPE_MULTIPART = "multipart/form-data"
        @JvmField val BODY_DATA_NAME = "<next-web::body.data:raw-data:key>"
    }

    init {
        createTime = System.currentTimeMillis()
        context = ctx
        servletRequest = request
        contextPath = request.contextPath
        val uri = request.requestURI
        path = if ("/" == contextPath) uri else uri.substring(contextPath.length)
        method = request.method.toUpperCase()
        host = servletRequest.getHeader("host")?:""
        ip = servletRequest.remoteAddr?:""
        port = servletRequest.remotePort
        userAgent = servletRequest.getHeader("User-Agent")?:""
        contentType = servletRequest.contentType ?: ""
        resources = splitToArray(path)
        comparator = Comparator.createRequest(method, path, resources)
    }

    /**
     * 读取BodyData (InputStream) 的文本数据。
     * 允许重复读取。第一次读取BodyData后，Request会将数据缓存到 requestParams.BODY_DATA_NAME 中。
     * HTTP 的各个方法的数据读取逻辑：
     * - GET/POST 在调用 body()时检查；
     * - PUT/DELETE 在调用任意params相关接口时才检查和加载（LazyLoad）
     * @return 文本数据。如果不存在数据则返回 null
     */
    fun body(): String? {
        val cached = requestParams[BODY_DATA_NAME]?.firstOrNull()
        if(cached == null && method in GET_POST) {
            val data = readBodyStream()
            if(data != null) {
                requestParams.put(BODY_DATA_NAME, mutableListOf(data))
            }else{
                requestParams.put(BODY_DATA_NAME, mutableListOf(/*empty*/))
            }
            return data
        }else{
            return cached
        }
    }

    /**
     * 以字符串值方式返回指定name的请求Headers值。
     * @return 字符值，如果请求中不存在此name的值则返回 null
     */
    fun header(name: String): String? {
        return servletRequest.getHeader(name)
    }

    /**
     * 获取所有Header
     * - 单个数值的参数以 String 类型返回
     * - 多个数值的参数以 List<String> 类型返回
     * @return 非空AnyMap对象
     */
    fun headers(): KeyMap {
        val map = KeyMap(HashMap<String, Any>())
        servletRequest.headerNames?.let { headerNames->
            for (name in headerNames) {
                servletRequest.getHeaders(name)?.let { headers->
                    val list = headers.toList()
                    if(list.size == 1) {
                        map.put(name, list.first())
                    }else{
                        map.put(name, list)
                    }
                }
            }
        }
        return map
    }

    /**
     * 返回指定name值的Cookie。
     * @return Cookie 对象，如果请求中不存在此Cookie则返回 null
     */
    fun cookie(name: String): Cookie? {
        servletRequest.cookies?.let { cookies->
            cookies.forEach { cookie ->
                if(name.equals(cookie.name)) {
                    return cookie
                }
            }
        }
        return null
    }

    fun cookies(): List<Cookie> {
        val cookies = servletRequest.cookies
        return if(cookies != null) cookies.toList() else emptyList()
    }

    /**
     * 获取指定参数名的值。
     * - 请求中存在单个值，返回值本身；
     * - 请求中存在多个值，返回第一个值；
     * @return 参数值。如果请求中不存在此参数，返回 null
     */
    fun paramOrNull(name: String): String? {
        requestParams[name]?.let { values ->
            return values.firstOrNull()
        }
        return null
    }

    /**
     * 获取所有参数。
     * - 单个数值的参数以 String 类型返回
     * - 多个数值的参数以 List<String> 类型返回
     * @return 非空AnyMap对象
     */
    fun params(): KeyMap {
        val map = KeyMap(HashMap<String, Any>())
        for((k, v) in requestParams) {
            if(v.size == 1) {
                map.put(k, v.first())
            }else{
                map.put(k, v.toList())
            }
        }
        return map
    }


    /**
     * 增加多个参数对到请求中
     */
    fun putParams(params: Map<String, String>) {
        for((k, v) in params) {
            putParam(k, v)
        }
    }

    /**
     * 增加一个参数对到请求中
     */
    fun putParam(name: String, value: String) {
        putOrNew(name, value, requestParams)
    }

    /**
     * 增加一个参数对到请求中
     */
    fun putParam(name: String, value: Any) {
        putParam(name, value.toString())
    }

    /**
     * 移除一个参数
     */
    fun removeParam(name: String) {
        requestParams.remove(name)
    }

    fun paramAsString(name: String, def: String): String {
        val value = paramOrNull(name)
        if(value.isNullOrEmpty()) {
            return def
        }else{
            return value!!
        }
    }

    fun paramAsString(name: String): String {
        return paramAsString(name, "")
    }

    fun paramAsInt(name: String, def: Int): Int {
        val value = paramOrNull(name)
        if(value.isNullOrEmpty()) {
            return def
        }else{
            return value!!.toInt()
        }
    }

    fun paramAsInt(name: String): Int {
        return paramAsInt(name, 0)
    }

    fun paramAsLong(name: String, def: Long): Long {
        val value = paramOrNull(name)
        if(value.isNullOrEmpty()) {
            return def
        }else{
            return value!!.toLong()
        }
    }

    fun paramAsLong(name: String): Long {
        return paramAsLong(name, 0L)
    }

    fun paramAsFloat(name: String, def: Float): Float {
        val value = paramOrNull(name)
        if(value.isNullOrEmpty()) {
            return def
        }else{
            return value!!.toFloat()
        }
    }

    fun paramAsFloat(name: String): Float {
        return paramAsFloat(name, 0f)
    }

    fun paramAsDouble(name: String, def: Double): Double {
        val value = paramOrNull(name)
        if(value.isNullOrEmpty()) {
            return def
        }else{
            return value!!.toDouble()
        }
    }

    fun paramAsDouble(name: String): Double {
        return paramAsDouble(name, 0.0)
    }

    fun paramAsBoolean(name: String, def: Boolean): Boolean {
        val value = paramOrNull(name)
        if(value.isNullOrEmpty()) {
            return def
        }else{
            return value!!.toBoolean()
        }
    }

    fun paramAsBoolean(name: String): Boolean {
        return paramAsBoolean(name, false)
    }

    // dynamic params

    fun dynamic(name: String, defaultValue: String): String {
        val value = dynamicParams[name] as String?
        if(value != null) {
            return value
        }else{
            return defaultValue
        }
    }

    fun dynamic(name: String): String {
        return dynamic(name, "")
    }

    fun dynamicAsString(name: String): String {
        return dynamic(name)
    }

    fun dynamicAsInt(name: String): Int {
        return dynamic(name, "0").toInt()
    }

    fun dynamicAsLong(name: String): Long {
        return dynamic(name, "0").toLong()
    }

    fun dynamicAsFloat(name: String): Float {
        return dynamic(name, "0").toFloat()
    }

    fun dynamicAsDouble(name: String): Double {
        return dynamic(name, "0").toDouble()
    }

    fun dynamicAsBoolean(name: String): Boolean {
        return dynamic(name, "false").toBoolean()
    }

    /// framework methods

    internal fun putDynamics(params: Map<String, String>) {
        dynamicParams.putAll(params)
    }

    internal fun removeDynamics(){
        dynamicParams.clear()
    }

    private fun readBodyStream(): String? {
        val output = StringWriter()
        val input = InputStreamReader(servletRequest.inputStream)
        val count = StreamKit.copy(input, output)
        return if(count > 0) output.toString() else null
    }

    private fun putOrNew(name: String, value: String, map: MutableMap<String, MutableList<String>>) {
        val values = map[name]
        if(values != null) {
            values.add(value)
        }else{
            map.put(name, mutableListOf(value))
        }
    }
}