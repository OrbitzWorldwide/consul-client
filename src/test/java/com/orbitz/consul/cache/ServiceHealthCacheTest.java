package com.orbitz.consul.cache;

import com.google.common.collect.ImmutableMap;
import com.orbitz.consul.BaseIntegrationTest;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.health.ServiceHealth;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ServiceHealthCacheTest extends BaseIntegrationTest {

    @Test
    public void nodeCacheServicePassingTest() throws Exception {
        HealthClient healthClient = client.healthClient();
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId);
        client.agentClient().pass(serviceId);
        Thread.sleep(100);

        try (ServiceHealthCache svHealth = ServiceHealthCache.newCache(healthClient, serviceName)) {
            svHealth.start();
            svHealth.awaitInitialized(3, TimeUnit.SECONDS);

            ServiceHealthKey serviceKey = getServiceHealthKeyFromCache(svHealth, serviceId, 8080)
                    .orElseThrow(() -> new RuntimeException("Cannot find service key from serviceHealthCache"));

            ServiceHealth health = svHealth.getMap().get(serviceKey);
            assertNotNull(health);
            assertEquals(serviceId, health.getService().getId());

            client.agentClient().fail(serviceId);
            Thread.sleep(100);
            health = svHealth.getMap().get(serviceKey);
            assertNull(health);
        }
    }

    @Test
    public void testServicesAreUniqueByID() throws Exception {
        HealthClient healthClient = client.healthClient();
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();
        String serviceId2 = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId);
        client.agentClient().pass(serviceId);

        client.agentClient().register(8080, 20L, serviceName, serviceId2);
        client.agentClient().pass(serviceId2);

        try (ServiceHealthCache svHealth = ServiceHealthCache.newCache(healthClient, serviceName)) {
            svHealth.start();
            svHealth.awaitInitialized(3, TimeUnit.SECONDS);

            ServiceHealthKey serviceKey1 = getServiceHealthKeyFromCache(svHealth, serviceId, 8080)
                    .orElseThrow(() -> new RuntimeException("Cannot find service key 1 from serviceHealthCache"));

            ServiceHealthKey serviceKey2 = getServiceHealthKeyFromCache(svHealth, serviceId2, 8080)
                    .orElseThrow(() -> new RuntimeException("Cannot find service key 2 from serviceHealthCache"));

            ImmutableMap<ServiceHealthKey, ServiceHealth> healthMap = svHealth.getMap();
            assertEquals(healthMap.size(), 2);
            ServiceHealth health =healthMap.get(serviceKey1);
            ServiceHealth health2 = healthMap.get(serviceKey2);

            assertEquals(serviceId, health.getService().getId());
            assertEquals(serviceId2, health2.getService().getId());
        }
    }

    private static Optional<ServiceHealthKey> getServiceHealthKeyFromCache(ServiceHealthCache cache, String serviceId, int port) {
        return cache.getMap().keySet()
                .stream()
                .filter(key -> serviceId.equals(key.getServiceId()) && (port == key.getPort()))
                .findFirst();
    }

    @Test
    public void shouldNotifyListener() throws Exception {
        String serviceName = UUID.randomUUID().toString();
        String serviceId = UUID.randomUUID().toString();

        client.agentClient().register(8080, 20L, serviceName, serviceId);
        client.agentClient().pass(serviceId);

        ServiceHealthCache svHealth = ServiceHealthCache.newCache(client.healthClient(), serviceName);

        final List<Map<ServiceHealthKey, ServiceHealth>> events = new ArrayList<>();
        svHealth.addListener(new ConsulCache.Listener<ServiceHealthKey, ServiceHealth>() {
            @Override
            public void notify(Map<ServiceHealthKey, ServiceHealth> newValues) {
                events.add(newValues);
            }
        });

        svHealth.start();
        svHealth.awaitInitialized(1000, TimeUnit.MILLISECONDS);

        Thread.sleep(200);
        client.agentClient().deregister(serviceId);
        Thread.sleep(200);

        assertEquals(2, events.size());
        Map<ServiceHealthKey, ServiceHealth> event0 = events.get(0);

        assertEquals(1, event0.size());
        for (Map.Entry<ServiceHealthKey, ServiceHealth> kv : event0.entrySet()) {
            assertEquals(kv.getKey().getServiceId(), serviceId);
        }

        Map<ServiceHealthKey, ServiceHealth> event1 = events.get(1);
        assertEquals(0, event1.size());
        svHealth.stop();
    }

    @Test
    public void shouldNotifyLateListenersIfNoService() throws Exception {
        String serviceName = UUID.randomUUID().toString();

        ServiceHealthCache svHealth = ServiceHealthCache.newCache(client.healthClient(), serviceName);

        final List<Map<ServiceHealthKey, ServiceHealth>> events = new ArrayList<>();
        svHealth.addListener(new ConsulCache.Listener<ServiceHealthKey, ServiceHealth>() {
            @Override
            public void notify(Map<ServiceHealthKey, ServiceHealth> newValues) {
                events.add(newValues);
            }
        });

        svHealth.start();
        svHealth.awaitInitialized(1000, TimeUnit.MILLISECONDS);

        assertEquals(1, events.size());
        Map<ServiceHealthKey, ServiceHealth> event0 = events.get(0);
        assertEquals(0, event0.size());
        svHealth.stop();
    }

    @Test
    public void shouldNotifyLateListenersRaceCondition() throws Exception {
        String serviceName = UUID.randomUUID().toString();

        final ServiceHealthCache svHealth = ServiceHealthCache.newCache(client.healthClient(), serviceName);

        final AtomicInteger eventCount = new AtomicInteger(0);
        svHealth.addListener(new ConsulCache.Listener<ServiceHealthKey, ServiceHealth>() {
            @Override
            public void notify(Map<ServiceHealthKey, ServiceHealth> newValues) {
                eventCount.incrementAndGet();

                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        svHealth.addListener(new ConsulCache.Listener<ServiceHealthKey, ServiceHealth>() {
                            @Override
                            public void notify(Map<ServiceHealthKey, ServiceHealth> newValues) {
                                eventCount.incrementAndGet();
                            }
                        });
                    }
                });
                t.start();

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        svHealth.start();
        svHealth.awaitInitialized(1000, TimeUnit.MILLISECONDS);

        Thread.sleep(1000);
        assertEquals(2, eventCount.get());
        svHealth.stop();
    }
}
