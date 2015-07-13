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

import java.util.concurrent.CountDownLatch;

public class EdtManagerPool {
  private final EdtManagerFactory myFactory;
  private final int myPoolSize;
  private final EventDispatchThreadManager[] myManagers;
  private final int[] myWorkingAdapters;
  private final String myName;
  private int myCurManager;
  private int myCreatedManagersNum;
  private final Object myLock;

  public EdtManagerPool(String name, int poolSize, EdtManagerFactory factory) {
    myLock = new Object();

    myName = name;
    myPoolSize = poolSize;
    myFactory = factory;
    myManagers = new EventDispatchThreadManager[myPoolSize];
    myWorkingAdapters = new int[myPoolSize];
    myCurManager = 0;
    myCreatedManagersNum = 0;
  }

  public EventDispatchThreadManager createTaskManager(String name) {
    synchronized (myLock) {
      int cur = getCurManagerIndex();
      incWorkingAdapters(cur);
      return new EdtManagerAdapter(name, myManagers[cur], cur);
    }
  }

  private int getCurManagerIndex() {
    int cur = myCurManager++;
    if (myCurManager == myPoolSize) {
      myCurManager = 0;
    }
    return cur;
  }

  private void incWorkingAdapters(int index) {
    if (myWorkingAdapters[index] == 0) {
      if (myManagers[index] != null) {
        throw new IllegalStateException();
      }
      myManagers[index] = myFactory.createTaskManager(myName + "_" + index + "_" + myCreatedManagersNum++);
    }
    myWorkingAdapters[index]++;
  }

  private void decWorkingAdapters(int index) {
    synchronized (myLock) {
      myWorkingAdapters[index]--;
      if (myWorkingAdapters[index] == 0) {
        myManagers[index].kill();
        myManagers[index] = null;
      }
    }
  }

  //for test
  boolean isEmpty() {
    synchronized (myLock) {
      for (EventDispatchThreadManager manager : myManagers) {
        if (manager != null) {
          return false;
        }
      }
      return true;
    }
  }

  //for test
  boolean checkManager(EventDispatchThreadManager manager) {
    synchronized (myLock) {
      EdtManagerAdapter adapter = (EdtManagerAdapter) manager;
      return adapter.myManager == myManagers[adapter.myIndex];
    }
  }

  private class EdtManagerAdapter extends BaseEdtManager {
    //we can't use here only myIndex because myManagers[] is guarded by a lock
    private final EventDispatchThreadManager myManager;
    private final int myIndex;

    private EdtManagerAdapter(String name, EventDispatchThreadManager manager, int index) {
      super(name);
      myManager = manager;
      myIndex = index;
    }

    @Override
    public void finish() {
      super.finish();
      final CountDownLatch latch = new CountDownLatch(1);
      myManager.getEDT().schedule(new Runnable() {
          @Override
          public void run() {
            shutdown();
            latch.countDown();
          }
        });
      try {
        latch.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        decWorkingAdapters(myIndex);
      }
    }

    @Override
    public void kill() {
      super.kill();
      shutdown();
      decWorkingAdapters(myIndex);
    }

    @Override
    protected void doSchedule(Runnable runnable) {
      myManager.getEDT().schedule(runnable);
    }

    @Override
    protected Registration doSchedule(int delay, Runnable runnable) {
      return myManager.getEDT().schedule(delay, runnable);
    }

    @Override
    protected Registration doScheduleRepeating(int period, Runnable runnable) {
      return myManager.getEDT().scheduleRepeating(period, runnable);
    }
  }
}