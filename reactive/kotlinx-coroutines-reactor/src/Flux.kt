
/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package kotlinx.coroutines.reactor

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.reactive.*
import org.reactivestreams.Publisher
import reactor.core.CoreSubscriber
import reactor.core.publisher.*
import kotlin.coroutines.*
import kotlin.internal.LowPriorityInOverloadResolution

/**
 * Creates cold reactive [Flux] that runs a given [block] in a coroutine.
 * Every time the returned flux is subscribed, it starts a new coroutine in the specified [context].
 * Coroutine emits items with `send`. Unsubscribing cancels running coroutine.
 *
 * Coroutine context can be specified with [context] argument.
 * If the context does not have any dispatcher nor any other [ContinuationInterceptor], then [Dispatchers.Default] is used.
 *
 * Invocations of `send` are suspended appropriately when subscribers apply back-pressure and to ensure that
 * `onNext` is not invoked concurrently.
 *
 * | **Coroutine action**                         | **Signal to subscriber**
 * | -------------------------------------------- | ------------------------
 * | `send`                                       | `onNext`
 * | Normal completion or `close` without cause   | `onComplete`
 * | Failure with exception or `close` with cause | `onError`
 *
 * Method throws [IllegalArgumentException] if provided [context] contains a [Job] instance.
 *
 * **Note: This is an experimental api.** Behaviour of publishers that work as children in a parent scope with respect
 *        to cancellation and error handling may change in the future.
 */
@ExperimentalCoroutinesApi
public fun <T> flux(
    context: CoroutineContext = EmptyCoroutineContext,
    @BuilderInference block: suspend ProducerScope<T>.() -> Unit
): Flux<T> {
    require(context[Job] === null) { "Flux context cannot contain job in it." +
        "Its lifecycle should be managed via Disposable handle. Had $context" }
    return Flux.from(reactorPublish(GlobalScope, context, block))
}

@Deprecated(
    message = "CoroutineScope.flux is deprecated in favour of top-level flux",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("flux(context, block)")
) // Since 1.3.0, will be error in 1.3.1 and hidden in 1.4.0. Binary compatibility with Spring
@LowPriorityInOverloadResolution
public fun <T> CoroutineScope.flux(
    context: CoroutineContext = EmptyCoroutineContext,
    @BuilderInference block: suspend ProducerScope<T>.() -> Unit
): Flux<T> =
    Flux.from(reactorPublish(this, context, block))

private fun <T> reactorPublish(
    scope: CoroutineScope,
    context: CoroutineContext = EmptyCoroutineContext,
    @BuilderInference block: suspend ProducerScope<T>.() -> Unit
): Publisher<T> = Publisher { subscriber ->
    // specification requires NPE on null subscriber
    if (subscriber == null) throw NullPointerException("Subscriber cannot be null")
    require(subscriber is CoreSubscriber) { "Subscriber is not an instance of CoreSubscriber, context can not be extracted." }
    val currentContext = subscriber.currentContext()
    val reactorContext = (context[ReactorContext]?.context?.putAll(currentContext) ?: currentContext).asCoroutineContext()
    val newContext = scope.newCoroutineContext(context + reactorContext)
    val coroutine = PublisherCoroutine(newContext, subscriber)
    subscriber.onSubscribe(coroutine) // do it first (before starting coroutine), to avoid unnecessary suspensions
    coroutine.start(CoroutineStart.DEFAULT, coroutine, block)
}