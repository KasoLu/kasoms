/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package server;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Ronan
 */
public class ThreadManager {
    private static final ThreadManager instance = new ThreadManager();

    public static ThreadManager getInstance() {
        return instance;
    }

    private ThreadPoolExecutor tpe;

    private ThreadManager() {}

//    private class RejectedExecutionHandlerImpl implements RejectedExecutionHandler {
//
//        @Override
//        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
//            Thread t = new Thread(r);
//            t.start();
//        }
//
//    }

    public void newTask(Runnable r) {
        tpe.execute(r);
    }

    // TODO 这个方法比较捉鸡, tpe的初始化应该是放在构造函数里
    public void start() {
        // TODO 这个handler的实现是新开一个线程, 直接绕过了线程池
//        RejectedExecutionHandler reh = new RejectedExecutionHandlerImpl();
//        ThreadFactory tf = Executors.defaultThreadFactory();

        // TODO 这里选择的ArrayBlockingQueue和上面的handler从逻辑上看属于互斥的,
        // TODO 既然不会抛弃所有的任务, 那这里就应该选择无限制的LinkedBlockingQueue
//        tpe = new ThreadPoolExecutor(20, 1000, 77, SECONDS, new ArrayBlockingQueue<>(50), tf, reh);
        // 新的实现变更为无限制的LinkedBlockingQueue, 如果出现了被拒绝的任务(理论上不会有), 则直接抛出异常
        tpe = new ThreadPoolExecutor(
                20, 1000, 77, SECONDS,
                new LinkedBlockingQueue<>(),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void stop() {
        tpe.shutdown();
        try {
            tpe.awaitTermination(5, MINUTES);
        } catch (InterruptedException ie) {
        }
    }

}
