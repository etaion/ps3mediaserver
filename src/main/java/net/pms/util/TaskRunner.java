/*
 * PS3 Media Server, for streaming any medias to your PS3.
 * Copyright (C) 2011  G.Zsombor
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.pms.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background task executor and scheduler with a dynamic thread pool, where the threads are daemons.
 *  
 * @author zsombor
 *
 */
public class TaskRunner {

	final static Logger LOG = LoggerFactory.getLogger(TaskRunner.class);
	
	private static TaskRunner instance;
	
	public static synchronized TaskRunner getInstance() { 
		if (instance == null) {
			instance = new TaskRunner();
		}
		return instance;
	}
	
	private final ExecutorService executors = Executors.newCachedThreadPool(new ThreadFactory() {
		
		int counter = 0;
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "background-task-"+(counter++));
			t.setDaemon(true);
			return t;
		}
	});
	
	private final Map<String, Integer> counters = new HashMap<String, Integer>();
	private final Map<String, Lock> uniquenessLock = new HashMap<String, Lock> (); 
	
	public void submit(Runnable runnable) {
		executors.execute(runnable);
	}
	
	public <X> Future<X> submit(Callable<X> call) {
		return executors.submit(call);
	}
	
	/**
	 * Submit a named task for later execution.
	 * 
	 * @param name
	 * @param runnable
	 */
	public void submitNamed(final String name, final Runnable runnable) {
		submitNamed(name, false, runnable);
	}
	
	/**
	 * Submit a named task for later execution. If singletonTask is set to true, checked that tasks with the same name is not concurrently running.
	 * @param name
	 * @param runnable
	 * @param singletonTask
	 */
	public void submitNamed(final String name, final boolean singletonTask, final Runnable runnable) {
		submit(new Runnable() { 
			@Override
			public void run() {
				String prevName = Thread.currentThread().getName();
				boolean locked = false;
				try {
					if (singletonTask) {
						if (getLock(name).tryLock()) {
							locked = true;
							LOG.debug("singleton task "+name+" started.");
						} else {
							locked = false;
							LOG.debug("singleton task '"+name+"' already running, exiting.");
							return;
						}
					}
					Thread.currentThread().setName(prevName + '-' + name + '(' + getAndIncr(name) + ')');
					LOG.debug("task started");
					runnable.run();
					LOG.debug("task ended.");
				} finally {
					if (locked) {
						getLock(name).unlock();
					}
					Thread.currentThread().setName(prevName);
				}
			}
		});
		
	}
	
	protected Lock getLock(String name) {
		synchronized(uniquenessLock) {
			Lock lk = uniquenessLock.get(name);
			if (lk == null) {
				lk = new ReentrantLock();
				uniquenessLock.put(name, lk);
			}
			return lk;
		}
	}
	
	protected int getAndIncr(String name) {
		synchronized(counters) {
			Integer val = counters.get(name);
			int newVal = (val == null) ? 0 : val.intValue() + 1;
			counters.put(name, newVal);
			return newVal;
		}
	}
	
	public void shutdown() {
		executors.shutdown();
	}

	/**
	 * @return
	 * @see java.util.concurrent.ExecutorService#isTerminated()
	 */
	public boolean isTerminated() {
		return executors.isTerminated();
	}

	/**
	 * @param timeout
	 * @param unit
	 * @return
	 * @throws InterruptedException
	 * @see java.util.concurrent.ExecutorService#awaitTermination(long, java.util.concurrent.TimeUnit)
	 */
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return executors.awaitTermination(timeout, unit);
	}
	
}
