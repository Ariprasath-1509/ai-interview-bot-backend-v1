package com.benchreadiness.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fix 6.1: assessmentExecutor is bound to app.assessment.*, with defaults equal to the old hard-coded 5/10/50. */
class AsyncConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(AsyncConfig.class);

    @Test
    void defaultsWithoutAnyPropertiesAre5_10_50() {
        runner.run(ctx -> assertBounds(ctx.getBean("assessmentExecutor", ThreadPoolTaskExecutor.class), 5, 10, 50));
    }

    @Test
    void applicationYmlDefaultsAre5_10_50() throws Exception {
        // Load only application.yml (not its optional .env imports), as when no APP_ASSESSMENT_* env is set.
        List<PropertySource<?>> yml = new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
        runner
            .withInitializer(ctx -> yml.forEach(ps -> ctx.getEnvironment().getPropertySources().addLast(ps)))
            .run(ctx -> {
                assertThat(ctx.getEnvironment().getProperty("app.assessment.stale-run-minutes")).isEqualTo("15");
                assertBounds(ctx.getBean("assessmentExecutor", ThreadPoolTaskExecutor.class), 5, 10, 50);
            });
    }

    @Test
    void boundsAreOverridable() {
        runner
            .withPropertyValues(
                "app.assessment.core-pool-size=15",
                "app.assessment.thread-pool-size=15",
                "app.assessment.queue-capacity=500")
            .run(ctx -> assertBounds(ctx.getBean("assessmentExecutor", ThreadPoolTaskExecutor.class), 15, 15, 500));
    }

    private static void assertBounds(ThreadPoolTaskExecutor executor, int core, int max, int queue) {
        assertThat(executor.getCorePoolSize()).isEqualTo(core);
        assertThat(executor.getMaxPoolSize()).isEqualTo(max);
        assertThat(executor.getQueueCapacity()).isEqualTo(queue);
    }
}
