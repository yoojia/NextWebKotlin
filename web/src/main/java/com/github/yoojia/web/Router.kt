package com.github.yoojia.web

import java.util.*

/**
 * @author Yoojia Chen (yoojiachen@gmail.com)
 * @since 1.0
 */
class Router {

    private val modules = ArrayList<Module>()
    private val threadIndex = object : ThreadLocal<Int>() {
        override fun initialValue(): Int? = 0
    }// Supported to Java 7

    private val maxIndex: Int by lazy { modules.size }

    fun add(module: Module) {
        modules.add(module)
    }

    @Throws(Exception::class)
    fun route(request: Request, response: Response) {
        try{
            next(request, response, this)
        }finally{
            threadIndex.set(0/* reset */)
        }
    }

    @Throws(Exception::class)
    fun next(request: Request, response: Response, chain: Router) {
        val index = threadIndex.get()
        if (index != maxIndex) {
            //!! Set next index before process
            threadIndex.set(index + 1)
            modules[index].process(request, response, chain)
        }
    }

    fun shutdown(){
        modules.clear()
        threadIndex.remove()
    }

}