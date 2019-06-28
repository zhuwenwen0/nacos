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
package com.alibaba.nacos.api.naming.pojo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;

import java.util.HashMap;
import java.util.Map;

/**
 * Instance:服务的实例，用来向注册中心注册的实例
 *
 * @author nkorange
 */
public class Instance {

    /**
     * unique id of this instance.:实例id
     */
    private String instanceId;

    /**
     * instance ip :实例ip地址
     */
    private String ip;

    /**
     * instance port :端口
     */
    private int port;

    /**
     * instance weight : 权重
     */
    private double weight = 1.0D;

    /**
     * instance health status : 是否开启健康检查
     */
    private boolean healthy = true;

    /**
     * If instance is enabled to accept request : 实例是否启用接收请求
     */
    private boolean enabled = true;

    /**
     * If instance is ephemeral : 实例是否是短暂的
     *
     * @since 1.0.0
     */
    private boolean ephemeral = true;

    /**
     * cluster information of instance : 实例的集群
     */
    private String clusterName;

    /**
     * Service information of instance : 服务名称
     */
    private String serviceName;

    /**
     * user extended attributes : 附加属性
     */
    private Map<String, String> metadata = new HashMap<String, String>();

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEphemeral() {
        return ephemeral;
    }

    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    public String toInetAddr() {
        return ip + ":" + port;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Instance)) {
            return false;
        }

        Instance host = (Instance) obj;

        return strEquals(toString(), host.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    private static boolean strEquals(String str1, String str2) {
        return str1 == null ? str2 == null : str1.equals(str2);
    }

}
