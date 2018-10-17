package com.bonitasoft.perf

import com.bonitasoft.engine.api.APIClient
import org.apache.jmeter.config.Argument
import org.apache.jmeter.config.Arguments
import org.apache.jmeter.control.LoopController
import org.apache.jmeter.extractor.RegexExtractor
import org.apache.jmeter.extractor.RegexExtractor.USE_HDRS
import org.apache.jmeter.protocol.http.control.CacheManager
import org.apache.jmeter.protocol.http.control.CookieManager
import org.apache.jmeter.protocol.http.control.Header
import org.apache.jmeter.protocol.http.control.HeaderManager
import org.apache.jmeter.protocol.http.sampler.HTTPSampler
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy
import org.apache.jmeter.protocol.http.util.HTTPArgument
import org.apache.jmeter.save.SaveService
import org.apache.jmeter.testelement.TestPlan
import org.apache.jmeter.threads.ThreadGroup
import org.apache.jorphan.collections.HashTree
import org.bonitasoft.engine.bpm.bar.BusinessArchive
import org.bonitasoft.engine.bpm.bar.BusinessArchiveBuilder
import org.bonitasoft.engine.bpm.bar.actorMapping.Actor
import org.bonitasoft.engine.bpm.bar.actorMapping.ActorMapping
import org.bonitasoft.engine.bpm.process.ProcessDefinition
import org.bonitasoft.engine.bpm.process.ProcessDefinitionNotFoundException
import org.bonitasoft.engine.bpm.process.impl.ProcessDefinitionBuilder
import org.bonitasoft.engine.identity.UserNotFoundException
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files


class SimpleAutomaticTask : JMeterTestPlan {


    override var test: HashTree? = null


    override fun setup(apiClient: APIClient, host: String, port: String) {
        createUser(apiClient)
        apiClient.login("walter.bates", "bpm")
        val process: ProcessDefinition = createProcess(apiClient)
        apiClient.logout()

        test = createTest(process, host, port)

    }

    private fun createProcess(apiClient: APIClient): ProcessDefinition {
        try {
            val processDefinitionId = apiClient.processAPI.getProcessDefinitionId("SimpleProcessWithTask", "1.0")
            while (apiClient.processAPI.deleteArchivedProcessInstances(processDefinitionId, 0, 500) != 0L);
            while (apiClient.processAPI.deleteProcessInstances(processDefinitionId, 0, 500) != 0L);
            apiClient.processAPI.disableAndDeleteProcessDefinition(processDefinitionId)
        } catch (e: ProcessDefinitionNotFoundException) {
        }
        val businessArchive = createProcess()
        return apiClient.processAPI.deployAndEnableProcess(businessArchive)
    }

    private fun createProcess(): BusinessArchive? {
        val process = ProcessDefinitionBuilder().createNewInstance("SimpleProcessWithTask", "1.0")
                .setActorInitiator("actor")
                .addStartEvent("start")
                .addAutomaticTask("step1")
                .addEndEvent("end").addTerminateEventTrigger()
                .addTransition("start", "step1")
                .addTransition("step1", "end")
                .process

        return BusinessArchiveBuilder().createNewBusinessArchive()
                .setProcessDefinition(process)
                .setActorMapping(ActorMapping().apply {
                    addActor(Actor("actor").apply {
                        addUser("walter.bates")
                    })
                })
                .done()
    }

    private fun createUser(apiClient: APIClient) {
        apiClient.login("install", "install")
        try {
            apiClient.identityAPI.getUserByUserName("walter.bates")
        } catch (e: UserNotFoundException) {
            apiClient.identityAPI.createUser("walter.bates", "bpm")
        }
        apiClient.logout()
    }


    fun createTest(process: ProcessDefinition, host: String, port: String): HashTree {
        val resourceAsStream = javaClass.getResourceAsStream("/Single Automatic Task.jmx")
        val file = Files.createTempFile("test", ".jmx")
        var text = resourceAsStream.reader().readText()
        text = text.replace("___processID___", process.id.toString())
        text = text.replace("___serverURL___", host)
        text = text.replace("___serverPort___", port)

        Files.write(file, text.toByteArray())
        return SaveService.loadTree(file.toFile())

//        val cookieManager = CookieManager().apply {
//            clearEachIteration = true
//        }
//        val cacheManager = CacheManager().apply {
//            useExpires = true
//        }
//        val extractToken = RegexExtractor().apply {
//            regex = "Set-Cookie: X-Bonita-API-Token=(.*?);"
//            matchNumber = 1
//            template = "$1$"
//            refName = "xBonitaAPIToken"
//            setScopeVariable("xBonitaAPIToken")
//            setUseField(USE_HDRS)
//            defaultValue = "none"
//        }
//        val login = httpRequest(host, port).apply {
//            method = "POST"
//            path = "/bonita/loginservice"
//            arguments = Arguments().apply {
//                setArguments(ArrayList(listOf(
//                        HTTPArgument().apply { name = "username";value = "walter.bates"; isUseEquals = true } as Argument,
//                        HTTPArgument().apply { name = "password";value = "bpm"; isUseEquals = true },
//                        HTTPArgument().apply { name = "redirect";value = "false"; isUseEquals = true }
//                )))
//            }
//            useKeepAlive = true
//            addTestElement(extractToken)
//        }
//        val createProcess = httpRequest(host, port).apply {
//            method = "POST"
//            path = "/bonita/API/bpm/process/${process.id}/instantiation"
//            followRedirects = true
//            useKeepAlive = true
//            headerManager = HeaderManager().apply {
//                add(Header().apply {
//                    name = "X-Bonita-API-Token"
//                    value = "\${xBonitaAPIToken}"
//                })
//            }
//        }
//        val logout = httpRequest(host, port).apply {
//            method = "GET"
//            path = "/bonita/logoutservice?redirect=false"
//        }
//
//
//        val loopController = LoopController().apply {
//            loops = 15
//            addTestElement(cookieManager)
//            addTestElement(cacheManager)
//            addTestElement(login)
//            addTestElement(createProcess)
//            addTestElement(logout)
//            setFirst(true)
//            initialize()
//        }
//
//        val threadGroup = ThreadGroup().apply {
//            numThreads = 2
//            rampUp = 1
//            setSamplerController(loopController)
//        }
//        val testPlan = TestPlan("Single Automatic Task")
//        testPlan.addThreadGroup(threadGroup)
//
//
//        val tree = HashTree().apply {
//            add(testPlan).apply {
//                add(threadGroup).apply {
//                    add(cookieManager)
//                    add(cacheManager)
//                    add(login).apply {
//                        add(extractToken)
//                    }
//                    add(createProcess)
//                    add(logout)
//                }
//            }
//        }
//        SaveService.saveTree(tree, FileOutputStream(File("test.jmx")))

//        return tree
    }
//
//    private fun httpRequest(host: String, port: String): HTTPSampler {
//        val login = HTTPSampler()
//        login.protocol = "http"
//        login.domain = host
//        login.port = port.toInt()
//        return login
//    }
}