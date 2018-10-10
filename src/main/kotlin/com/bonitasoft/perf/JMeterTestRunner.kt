package com.bonitasoft.perf


fun main(args: Array<String>) {
    println("Running test on Bonita host:${args[0]} and port:${args[1]}")
    JMeterTest().run(args[0],args[1])
}