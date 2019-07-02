/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.client.naming.beat;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;

import java.util.Map;
import java.util.concurrent.*;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * 心跳检测
 *
 * @author harold
 */
public class BeatReactor {

    private ScheduledExecutorService executorService;

    private volatile long clientBeatInterval = 5 * 1000;

    private NamingProxy serverProxy;

    public final Map<String, BeatInfo> dom2Beat = new ConcurrentHashMap<String, BeatInfo>();

    public BeatReactor(NamingProxy serverProxy) {
        this(serverProxy, UtilAndComs.DEFAULT_CLIENT_BEAT_THREAD_COUNT);
    }

    public BeatReactor(NamingProxy serverProxy, int threadCount) {
        this.serverProxy = serverProxy;

        //心跳发送的线程池，设置为守护线程
        executorService = new ScheduledThreadPoolExecutor(threadCount, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("com.alibaba.nacos.naming.beat.sender");
                return thread;
            }
        });

        //执行心跳处理
        executorService.schedule(new BeatProcessor(), 0, TimeUnit.MILLISECONDS);
    }

    public void addBeatInfo(String serviceName, BeatInfo beatInfo) {
        NAMING_LOGGER.info("[BEAT] adding beat: {} to beat map.", beatInfo);
        dom2Beat.put(buildKey(serviceName, beatInfo.getIp(), beatInfo.getPort()), beatInfo);
        MetricsMonitor.getDom2BeatSizeMonitor().set(dom2Beat.size());
    }

    public void removeBeatInfo(String serviceName, String ip, int port) {
        NAMING_LOGGER.info("[BEAT] removing beat: {}:{}:{} from beat map.", serviceName, ip, port);
        dom2Beat.remove(buildKey(serviceName, ip, port));
        MetricsMonitor.getDom2BeatSizeMonitor().set(dom2Beat.size());
    }

    public String buildKey(String serviceName, String ip, int port) {
        return serviceName + Constants.NAMING_INSTANCE_ID_SPLITTER
            + ip + Constants.NAMING_INSTANCE_ID_SPLITTER + port;
    }

    /**
     * 心跳处理
     */
    class BeatProcessor implements Runnable {

        @Override
        public void run() {
            try {
                for (Map.Entry<String, BeatInfo> entry : dom2Beat.entrySet()) {
                    BeatInfo beatInfo = entry.getValue();
                    //如果是在这个周期已经发送过心跳了，就直接跳出
                    if (beatInfo.isScheduled()) {
                        continue;
                    }
                    //然后设置这个心跳已经在这个周期发送过心跳了
                    beatInfo.setScheduled(true);
                    //调用BeatTask，来进行发送心跳，而BeatTask的作用就是发送一次心跳，然后设置scheduled变为false
                    //以便在下一次心跳检测的时候，能够继续发送心跳
                    executorService.schedule(new BeatTask(beatInfo), 0, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                NAMING_LOGGER.error("[CLIENT-BEAT] Exception while scheduling beat.", e);
            } finally {
                //在第一次发送之后，会返回时间间隔，这时候就会设置clientBeatInterval,
                // 这个时候这个线程就会每隔clientBeatInterval这个时间就会检测一下心跳,观察是否有新加入的服务
                executorService.schedule(this, clientBeatInterval, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * 心跳的任务
     */
    class BeatTask implements Runnable {

        BeatInfo beatInfo;

        public BeatTask(BeatInfo beatInfo) {
            this.beatInfo = beatInfo;
        }

        @Override
        public void run() {
            //向nacos服务发送心跳
            long result = serverProxy.sendBeat(beatInfo);
            beatInfo.setScheduled(false);
            if (result > 0) {
                clientBeatInterval = result;
            }
        }
    }
}
