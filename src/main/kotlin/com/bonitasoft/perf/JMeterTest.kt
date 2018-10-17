package com.bonitasoft.perf

import com.bonitasoft.engine.api.APIClient
import org.apache.jmeter.engine.StandardJMeterEngine
import org.apache.jmeter.reporters.ResultCollector
import org.apache.jmeter.reporters.Summariser
import org.apache.jmeter.util.JMeterUtils
import org.bonitasoft.engine.api.ApiAccessType
import org.bonitasoft.engine.util.APITypeManager
import java.io.File


class JMeterTest {

    fun run(host: String, port: String) {
        val jmeterDir = createTempDir("jmeter")
        val jmeter = createEngine(jmeterDir)


        val test = SimpleAutomaticTask()

        val apiClient = APIClient()

        APITypeManager.setAPITypeAndParams(ApiAccessType.HTTP, mapOf(
                "server.url" to "http://$host:$port",
                "application.name" to "bonita"
        ))
        test.setup(apiClient, host, port)


        configureOutput(jmeterDir, test)

        // Run Test Plan

        runTest(jmeter, test)
    }

    private fun runTest(jmeter: StandardJMeterEngine, test: JMeterTestPlan) {
        jmeter.configure(test.test)
        jmeter.run()
    }

    private fun configureOutput(jmeterDir: File, test: JMeterTestPlan) {
        // save generated test plan to JMeter's .jmx file format
        //        SaveService.saveTree(testPlanTree, FileOutputStream("test.jmx"))

        //add Summarizer output to get test progress in stdout like:
        // summary =      2 in   1.3s =    1.5/s Avg:   631 Min:   290 Max:   973 Err:     0 (0.00%)
        var summer: Summariser? = null
        val summariserName = JMeterUtils.getPropDefault("summariser.name", "summary")
        if (summariserName.length > 0) {
            summer = Summariser(summariserName)
        }


        // Store execution results into a .jtl file
        println("results in ${jmeterDir.resolve("test.jtl").absolutePath}")
        val logFile = "test.jtl"
        val logger = ResultCollector(summer)
        logger.filename = logFile
        test.test!!.add(test.test!!.array[0], logger)
    }

    private fun createEngine(jmeterDir: File): StandardJMeterEngine {
        val jmeter = StandardJMeterEngine()
        JMeterUtils.loadJMeterProperties("none")
        JMeterUtils.setJMeterHome(jmeterDir.absolutePath)
        JMeterUtils.initLocale()
        val saveServiceProperties = this::class.java.getResource("/saveservice.properties").readText()
        val bin = jmeterDir.resolve("bin")
        bin.mkdir()
        val saveServicePropertiesFile = bin.resolve("saveservice.properties")
        saveServicePropertiesFile.createNewFile()
        saveServicePropertiesFile.writeText(saveServiceProperties)
        return jmeter
    }


}