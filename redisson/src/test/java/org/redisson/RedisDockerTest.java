package org.redisson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.*;
import org.redisson.config.Config;
import org.redisson.config.Protocol;
import org.redisson.misc.RedisURI;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class RedisDockerTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.2")
                    .withExposedPorts(6379);

    protected static RedissonClient redisson;

    protected static RedissonClient redissonCluster;

    private static GenericContainer<?> REDIS_CLUSTER;

    @BeforeAll
    public static void beforeAll() {
        Config config = createConfig();
        redisson = Redisson.create(config);
    }

    protected static Config createConfig() {
        Config config = new Config();
        config.setProtocol(Protocol.RESP2);
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:" + REDIS.getFirstMappedPort());
        return config;
    }

    protected static RedissonClient createInstance() {
        Config config = createConfig();
        return Redisson.create(config);
    }

    protected void testInCluster(Consumer<RedissonClient> redissonCallback) {
        if (redissonCluster == null) {
            REDIS_CLUSTER = new GenericContainer<>("vishnunair/docker-redis-cluster")
                            .withExposedPorts(6379, 6380, 6381, 6382, 6383, 6384)
                            .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(10)));
            REDIS_CLUSTER.start();

            Config config = new Config();
            config.setProtocol(Protocol.RESP2);
            config.useClusterServers()
                    .setNatMapper(new NatMapper() {
                        @Override
                        public RedisURI map(RedisURI uri) {
                            if (REDIS_CLUSTER.getMappedPort(uri.getPort()) == null) {
                                return uri;
                            }
                            return new RedisURI(uri.getScheme(), REDIS_CLUSTER.getHost(), REDIS_CLUSTER.getMappedPort(uri.getPort()));
                        }
                    })
                    .addNodeAddress("redis://127.0.0.1:" + REDIS_CLUSTER.getFirstMappedPort());
            redissonCluster = Redisson.create(config);
        }

        redissonCallback.accept(redissonCluster);
    }

    @BeforeEach
    public void beforeEach() {
        redisson.getKeys().flushall();
        if (redissonCluster != null) {
            redissonCluster.getKeys().flushall();
        }
    }

    @AfterAll
    public static void afterAll() {
        redisson.shutdown();
        if (redissonCluster != null) {
            redissonCluster.shutdown();
            redissonCluster = null;
        }
        if (REDIS_CLUSTER != null) {
            REDIS_CLUSTER.stop();
            REDIS_CLUSTER = null;
        }
    }

}
