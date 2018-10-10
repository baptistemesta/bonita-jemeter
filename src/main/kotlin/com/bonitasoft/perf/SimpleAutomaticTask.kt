package com.bonitasoft.perf

import com.bonitasoft.engine.api.APIClient
import org.apache.jmeter.config.Argument
import org.apache.jmeter.config.Arguments
import org.apache.jmeter.control.LoopController
import org.apache.jmeter.protocol.http.control.CacheManager
import org.apache.jmeter.protocol.http.control.CookieManager
import org.apache.jmeter.protocol.http.sampler.HTTPSampler
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
                    addActor(Actor().apply {
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


        val cookieManager = CookieManager().apply {
            clearEachIteration = true
        }
        val cacheManager = CacheManager().apply {
            useExpires = true
        }
        val login = httpRequest(host, port).apply {
            method = "POST"
            path = "/bonita/loginservice"
            arguments = Arguments().apply {
                setArguments(listOf(
                        Argument().apply { name = "username";value = "walter.bates" },
                        Argument().apply { name = "password";value = "bpm" },
                        Argument().apply { name = "redirect";value = "false" }
                ))
            }
        }
        val createProcess = httpRequest(host, port).apply {
            method = "POST"
            path = "/bonita/API/bpm/process/${process.id}/instantiation"
        }
        val logout = httpRequest(host, port).apply {
            method = "GET"
            path = "/bonita/logoutservice?redirect=false"
        }


        val loopController = LoopController().apply {
            loops = 15
            addTestElement(cookieManager)
            addTestElement(cacheManager)
            addTestElement(login)
            addTestElement(createProcess)
            addTestElement(logout)
            setFirst(true)
            initialize()
        }

        val threadGroup = ThreadGroup().apply {
            numThreads = 2000
            rampUp = 200
            setSamplerController(loopController)
        }
        val testPlan = TestPlan("Single Automatic Task")


        return HashTree().apply {
            add(testPlan,threadGroup)
        }
    }

    private fun httpRequest(host: String, port: String): HTTPSampler {
        val login = HTTPSampler()
        login.protocol = "http"
        login.domain = host
        login.port = port.toInt()
        return login
    }
}