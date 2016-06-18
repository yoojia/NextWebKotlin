package com.github.yoojia.web.util

import java.io.Reader
import java.io.Writer
import java.util.*

fun now(): Long {
    return System.currentTimeMillis()
}

fun escape(start: Long): Long {
    return now() - start
}

fun <E> List<E>.concat(b: List<E>): List<E> {
    val out = ArrayList<E>(this)
    out.addAll(b)
    return out.toList()
}

fun streamCopy(from: Reader, to: Writer): Int {
    val buffer = CharArray(1024 * 4)
    var count = 0
    var n: Int
    while (true) {
        n = from.read(buffer)
        if(n == -1) break
        to.write(buffer, 0, n)
        count += n
    }
    return count
}