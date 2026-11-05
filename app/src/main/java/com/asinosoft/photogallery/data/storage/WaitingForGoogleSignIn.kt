package com.asinosoft.photogallery.data.storage

import kotlinx.coroutines.CancellationException

/**
 * Drive ждёт UI consent. CancellationException не роняет процесс
 * (в отличие от checkNotNull / IllegalStateException на main).
 */
class WaitingForGoogleSignIn :
    CancellationException("Waiting for Google sign-in")
