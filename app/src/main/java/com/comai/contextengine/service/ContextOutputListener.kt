package com.comai.contextengine.service

import com.comai.contextengine.contract.SharedContextContract

interface ContextOutputListener {
    fun onContextEscalated(jsonContract: String, contractObject: SharedContextContract)
}
