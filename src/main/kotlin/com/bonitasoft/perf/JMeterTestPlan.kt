package com.bonitasoft.perf

import com.bonitasoft.engine.api.APIClient
import org.apache.jorphan.collections.HashTree

interface JMeterTestPlan {


    var test: HashTree?

    fun setup(apiClient: APIClient, host: String, port: String)
    
}