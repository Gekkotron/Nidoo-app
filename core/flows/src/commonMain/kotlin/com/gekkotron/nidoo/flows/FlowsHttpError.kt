package com.gekkotron.nidoo.flows

/** The hub answered a flow toggle with a non-2xx [status]. */
class FlowsHttpError(val status: Int) : Exception("the hub answered HTTP $status")
