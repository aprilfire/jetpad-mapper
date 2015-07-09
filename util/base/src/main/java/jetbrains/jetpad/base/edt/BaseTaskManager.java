/*
 * Copyright 2012-2015 JetBrains s.r.o
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jetbrains.jetpad.base.edt;

import jetbrains.jetpad.base.Registration;

public abstract class BaseTaskManager implements TaskManager, EventDispatchThread {
  private volatile boolean myFinished;
  private final String myName;

  public BaseTaskManager(String name) {
    myName = name;
  }

  @Override
  public final EventDispatchThread getEDT() {
    return this;
  }

  @Override
  public void finish() {
    checkCanStop();
  }

  @Override
  public void kill() {
    checkCanStop();
  }

  @Override
  public boolean isStopped() {
    return myFinished;
  }

  public String getName() {
    return myName;
  }

  protected void shutdown() {
    myFinished = true;
  }

  private void checkCanStop() {
    if (myFinished) {
      throw new IllegalStateException(wrapMessage("has already been stopped"));
    }
  }

  @Override
  public final void schedule(Runnable r) {
    if (isStopped()) {
      return;
    }
    doSchedule(r);
  }

  protected abstract void doSchedule(Runnable runnable);

  @Override
  public final Registration schedule(int delay, Runnable r) {
    if (isStopped()) {
      return Registration.EMPTY;
    }
    return doSchedule(delay, r);
  }

  protected abstract Registration doSchedule(int delay, Runnable runnable);

  @Override
  public final Registration scheduleRepeating(int period, Runnable r) {
    if (isStopped()) {
      return Registration.EMPTY;
    }
    return doScheduleRepeating(period, r);
  }

  protected abstract Registration doScheduleRepeating(int period, Runnable runnable);

  protected String wrapMessage(String message) {
    return this + ": " + message;
  }

  @Override
  public String toString() {
    String name = getClass().getName();
    int dotIndex = name.lastIndexOf('.');
    String className = dotIndex == 1 ? name : name.substring(dotIndex + 1);
    return className + "@" + Integer.toHexString(hashCode()) + ("".equals(myName) ? "" : " (" + getName() + ")");
  }

}
