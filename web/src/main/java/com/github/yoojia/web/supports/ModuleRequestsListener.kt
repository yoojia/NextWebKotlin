package com.github.yoojia.web.supports

import com.github.yoojia.web.Request
import com.github.yoojia.web.Response
import java.lang.reflect.Method

/**
 * 模块内各个方法触发执行监听接口
 * @author Yoojia Chen (yoojiachen@gmail.com)
 * @since 2.3
 */
interface ModuleRequestsListener : ModuleListener{

    /**
     * 在模块内每个请求执行前回调
     */
    fun eachBefore(method: Method, request: Request, response: Response)

    /**
     * 在模块内每个请求执行后回调
     */
    fun eachAfter(method: Method, request: Request, response: Response)
}