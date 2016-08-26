package com.adison.gradle.plugin

/**
 * 调试工具
 * @author adison
 */
class DebugUtils {
    static debug=true
    static void debug(String msg) {
        if(debug)
        println(msg)
    }

    static void error(String msg) {
        println(msg)
    }
}
