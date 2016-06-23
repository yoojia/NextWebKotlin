package com.github.yoojia.web

import com.github.yoojia.web.core.ClassProvider
import com.github.yoojia.web.core.Engine
import javax.servlet.Servlet
import javax.servlet.ServletConfig
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

/**
 * @author Yoojia Chen (yoojiachen@gmail.com)
 * @since 2.a.5
 */
abstract class ProvidedBootstrapServlet : Servlet, ClassProvider {

    override fun destroy() {
        Engine.shutdown()
    }

    override fun init(config: ServletConfig?) {
        Engine.boot(config?.servletContext!!, this)
    }

    override fun getServletConfig(): ServletConfig? {
        return null
    }

    override fun getServletInfo(): String? {
        return Engine.VERSION
    }

    override fun service(request: ServletRequest?, response: ServletResponse?) {
        Engine.process(request!!, response!!)
    }
}