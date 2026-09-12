package com.filmvault.app.util

import java.math.BigInteger

/**
 * PoW（工作量证明）求解器。
 *
 * 流程：
 *   GET  /res/pow  -> { "N": <hex>, "x": <hex>, "t": <int> }
 *   计算 y = x ^ (2 ^ t) mod N   （对 N 做 t 次平方同余）
 *   POST /res/pow  body: "y=<hex>"   -> 服务器下发 browser_verified cookie
 *
 * 与浏览器内 Web Worker 逻辑一致：从 y=x 开始，循环 t 次 y = (y*y) mod N。
 * 等价于 y = x^(2^t) mod N，可用 BigInteger.modPow 高效计算。
 */
object PowSolver {

    /** 求解并返回提交用的十六进制 y */
    fun solve(nHex: String, xHex: String, t: Int): String {
        val n = BigInteger(nHex, 16)
        var y = BigInteger(xHex, 16)
        // 与站点 Web Worker 保持一致：y = x^(2^t) mod N 等价于连续 t 次平方取模。
        // 避免构造约 400000 位的 2^t 大整数，显著降低手机端内存和计算开销。
        repeat(t) { y = y.multiply(y).mod(n) }
        return y.toString(16)
    }
}
