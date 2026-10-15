package com.hcloudcontrol.core.network

/** A successful-but-unexpected response cannot prove synchronous destructive completion. */
internal fun requireNoContent(response: retrofit2.Response<Unit>) {
    if (!response.isSuccessful) throw retrofit2.HttpException(response)
    if (response.code() != 204) throw java.io.IOException("Provider completion response not confirmed")
}
