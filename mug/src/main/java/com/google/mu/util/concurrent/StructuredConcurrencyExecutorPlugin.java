package com.google.mu.util.concurrent;

import java.util.concurrent.ExecutorService;

/**
 * SPI Used by {@link java.util.ServiceLoader} to plug in the executor used by {@link Fanout}.
 *
 * <p>Steps to create a plugin:
 *
 * <ul>
 *   <li>Create a public subclass of {@link StructuredConcurrencyExecutorPlugin} and override {@link
 *       #createExecutor}.
 *   <li>Annotate your class with {@code @AutoService(StructuredConcurrencyExecutorPlugin.class)}.
 * </ul>
 *
 * @since 8.1
 */
public abstract class StructuredConcurrencyExecutorPlugin {
  /**
   * Subclasses override this method to create the executor that runs all structured concurrency
   * fanout tasks. The returned executor will be used as a singleton and never shut down. It's
   * recommended for the executor to use virtual and daemon threads.
   */
  protected abstract ExecutorService createExecutor();

  /**
   * Returns the priority of this plugin when multiple plugins are available. Defaults to {@link
   * Priority#FRAMEWORK_STANDARD}, intended for server frameworks to implement.
   */
  protected Priority priority() {
    return Priority.FRAMEWORK_STANDARD;
  }

  /**
   * Plugin priority from low to high. A plugin with higher priority will be picked over lower
   * priority.
   */
  enum Priority {
    /** For server framework authors to implement a framework-configured standard plugin. */
    FRAMEWORK_STANDARD,

    /**
     * For application (server) owners to override any framework or library plugins.
     *
     * <p>For example, if you need structured concurrency but not virtual threads, perhaps because
     * your application is a command-line tool or batch-processing pipeline where blocking a few OS
     * threads isn't a problem, you may create a non-virtual-thread plugin at APPLICATION_SPECIFIC
     * priority so that you don't need to worry about go/virtual-thread-optin.
     */
    APPLICATION_SPECIFIC,
  }

  @Override
  public String toString() {
    return getClass().getName();
  }
}