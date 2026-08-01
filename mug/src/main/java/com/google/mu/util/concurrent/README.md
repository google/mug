The virtual threads in Java 21 is an exciting and transformative new feature.

All your server code that currently uses various async frameworks with chained callbacks can be gone.

Already, you can write intuitive code to call outgoing rpc, wait for the result, handle any exception that may occur all in a single method. You won't be guilty of blocking precious platform thread and in turn degrading your server's throughput.

But sometimes when you serve a request, you need to invoke several outgoing rpcs, or read from db. Doing them sequentially results in longer request latency and you spend most of the time waiting.

It makes sense to do these IO-bound operations concurrently. [JEP 453](https://openjdk.org/jeps/453) is still in preview. And the API as it is still feels a bit verbose. So we created a simpler API ([Fanout](https://google.github.io/mug/apidocs/com/google/mu/util/concurrent/Fanout.html)). It focuses on the most common fanout use case where the [ShutDownOnFailure](https://docs.oracle.com/en/java/javase/20/docs/api/jdk.incubator.concurrent/jdk/incubator/concurrent/StructuredTaskScope.ShutdownOnFailure.html) stragegy is sufficient.

For example if you need to concurrently read from db, and invoke a rpc to fetch some data:

```java {.good}
import static com.google.mu.util.concurrent.Fanout.concurrently;

...
Result calculateBilling() {
  return concurrently(
      () -> readJobTimelineFromDb(...),
      () -> callServiceForLatestAccountInfo(...),
      (timeline, accountInfo) -> ...);
}
```

Up to 5 concurrent fanout operations are supported.

What you get:

* Reading from db and calling rpcs are executed concurrently in virtual threads.
* Return values from the concurrent operations are passed to the lambda to be combined.
* If any of the concurrent operations fails, the other concurrent operation(s) are canceled, and the exception is propagated to the main thread so you can catch and handle it.
* If the main thread is interrupted while waiting for the concurrent operations, the currently ongoing concurrent operations will be canceled.

By default, the library uses `Executors.newVirtualThreadPerTaskExecutor()` to run the concurrent tasks in virtual threads. But sometimes your org may want to enforce extra context propagation, monitoring or profiling that require a special `Executor`. For these cases, you can create a subclass of `StructuredConcurrencyExecutorPlugin` and implement the `createExecutor()` method. Then put the class name in the `META-INF/services/com.google.mu.util.concurrent.StructuredConcurrencyExecutorPlugin` file for it to be accessible through `java.util.ServiceLoader`.

Optionally you can use Google [`@AutoService`](http://github.com/google/auto/tree/main/service) to automate the `META-INF` part.