package ru.hh.consul.cache;

import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import ru.hh.consul.KeyValueClient;
import ru.hh.consul.KeyValueClientFactory;
import ru.hh.consul.MockApiService;
import ru.hh.consul.config.CacheConfig;
import ru.hh.consul.config.ClientConfig;
import ru.hh.consul.model.kv.ImmutableValue;
import ru.hh.consul.model.kv.Value;
import ru.hh.consul.monitoring.ClientEventCallback;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import retrofit2.Retrofit;
import retrofit2.mock.BehaviorDelegate;
import retrofit2.mock.MockRetrofit;
import retrofit2.mock.NetworkBehavior;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@RunWith(JUnitParamsRunner.class)
public class KVCacheTest {

    @Test
    @Parameters(method = "getKeyValueTestValues")
    @TestCaseName("wanted {0}, found {1}")
    public void checkKeyExtractor(String rootPath, String input, String expected) {
        //Called in the constructor of the cache, must be use in the test as it may modify rootPath value.
        final String keyPath = KVCache.prepareRootPath(rootPath);

        Function<Value, String> keyExtractor = KVCache.getKeyExtractorFunction(keyPath);
        Assert.assertEquals(expected, keyExtractor.apply(createValue(input)));
    }

    public Object getKeyValueTestValues() {
        return new Object[]{
                new Object[]{"", "a/b", "a/b"},
                new Object[]{"/", "a/b", "a/b"},
                new Object[]{"a", "a/b", "a/b"},
                new Object[]{"a/", "a/b", "b"},
                new Object[]{"a/b", "a/b", ""},
                new Object[]{"a/b", "a/b/", "b/"},
                new Object[]{"a/b", "a/b/c", "b/c"},
                new Object[]{"a/b", "a/bc", "bc"},
                new Object[]{"a/b/", "a/b/", ""},
                new Object[]{"a/b/", "a/b/c", "c"},
                new Object[]{"/a/b", "a/b", ""}
        };
    }

    private Value createValue(final String key) {
        return ImmutableValue.builder()
                .createIndex(1234567890)
                .modifyIndex(1234567890)
                .lockIndex(1234567890)
                .flags(1234567890)
                .key(key)
                .value(Optional.empty())
                .build();
    }

    @Test
    public void testListenerWithMockRetrofit() throws InterruptedException {
        final Retrofit retrofit = new Retrofit.Builder()
                // For safety, this is a black hole IP: see RFC 6666
                .baseUrl("http://[100:0:0:0:0:0:0:0]/")
                .build();
        final NetworkBehavior networkBehavior = NetworkBehavior.create();
        networkBehavior.setDelay(0, TimeUnit.MILLISECONDS);
        networkBehavior.setErrorPercent(0);
        networkBehavior.setFailurePercent(0);
        final MockRetrofit mockRetrofit = new MockRetrofit.Builder(retrofit)
                .networkBehavior(networkBehavior)
                .backgroundExecutor(Executors.newFixedThreadPool(1, new ThreadFactory() {
                    final AtomicInteger counter = new AtomicInteger();
                    @Override
                    public Thread newThread(@NotNull Runnable r) {
                      Thread thread = new Thread(r, "mockRetrofitBackground-" + counter.getAndIncrement());
                      return thread;
                    }
                  }))
                .build();

        final BehaviorDelegate<KeyValueClient.Api> delegate = mockRetrofit.create(KeyValueClient.Api.class);
        final MockApiService mockApiService = new MockApiService(delegate);


        final CacheConfig cacheConfig = CacheConfig.builder()
                .withMinDelayBetweenRequests(Duration.ofSeconds(10))
                .build();

        final KeyValueClient kvClient = KeyValueClientFactory.create(mockApiService, new ClientConfig(cacheConfig),
                new ClientEventCallback() {
        });


        try (final KVCache kvCache = KVCache.newCache(kvClient, "")) {
            kvCache.addListener(new AlwaysThrowsListener());
            final StubListener goodListener = new StubListener();
            kvCache.addListener(goodListener);

            kvCache.start();

            final StopWatch stopWatch = new StopWatch();

            // Make sure that we wait some duration of time for asynchronous things to occur
            while (stopWatch.getTime() < 5000 && goodListener.getCallCount() < 1) {
                Thread.sleep(50);
            }

            Assert.assertEquals(1, goodListener.getCallCount());
        }

    }
}
