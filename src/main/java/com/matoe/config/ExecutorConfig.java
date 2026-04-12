package com.matoe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Virtual-thread executor for agent fan-out.
 *
 * <p>All concurrent agent dispatch goes through this executor instead of the
 * default {@code ForkJoinPool.commonPool} used by bare
 * {@code CompletableFuture.supplyAsync(...)}. Virtual threads (Project Loom,
 * Java 21+) are ideal for the agent workload — each agent does HTTP I/O
 * (browser service or LLM API), so we can support 100+ concurrent sessions
 * with 14 agents each (~1400 virtual threads) without exhausting a small
 * fork-join pool.
 *
 * <p>Bean name: {@code agentExecutor}. Inject with
 * {@code @Qualifier("agentExecutor")}.
 */
@Configuration
public class ExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(ExecutorConfig.class);

    @Bean(name = "agentExecutor", destroyMethod = "shutdown")
    public ExecutorService agentExecutor() {
        log.info("Creating virtual-thread executor for agent dispatch (Java 21 Loom)");
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
