/*
 * MIT License
 *
 * Copyright (c) 2019 Nailah Azeez, Jaskiran Lamba, Sandeep Suri, Kent Tsuenchy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hkkt.communication;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 *
 * @author Kent Tsuenchy
 */
public class TaskHandler {
  private static final Logger LOG = Logger.getLogger(TaskHandler.class.getName());

  /**
   *
   */
  protected final ThreadPoolExecutor executor;

  /**
   *
   */
  public TaskHandler() {
    this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
  }

  /**
   *
   */
  public void cleanup() {
    this.executor.shutdown();

    try {
      if (!this.executor.awaitTermination(1000, TimeUnit.MILLISECONDS))
        this.executor.shutdownNow();
    } catch (InterruptedException ex) {
      this.executor.shutdownNow();
    }
  }

  /**
   *
   * @param r
   */
  public void startTask(Runnable r) {
    this.executor.submit(r);
  }

  /**
   *
   * @param <T>
   * @param c
   * @return
   */
  public <T> Future<T> startTask(Callable<T> c) {
    return this.executor.submit(c);
  }
}
