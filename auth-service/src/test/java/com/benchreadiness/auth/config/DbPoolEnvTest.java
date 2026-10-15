package com.benchreadiness.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/**
 * Report item 43: the Hikari pool settings are per-service environment variables ({@code AUTH_DB_*})
 * whose defaults must stay exactly the old hard-coded values. Resolves the real application.yml
 * against an isolated environment, so no database and no host environment variables are involved.
 */
class DbPoolEnvTest {

    private static PropertySourcesPropertyResolver resolver(Map<String, Object> env) throws IOException {
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("test-env", env));
        yaml.forEach(sources::addLast);
        return new PropertySourcesPropertyResolver(sources);
    }

    @Test
    void defaultsAreTheOldHardCodedValuesWhenNoEnvVarIsSet() throws IOException {
        PropertySourcesPropertyResolver r = resolver(Map.of());
        assertThat(r.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("5");
        assertThat(r.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo("1");
        assertThat(r.getProperty("spring.datasource.hikari.connection-timeout")).isEqualTo("120000");
        assertThat(r.getProperty("spring.datasource.hikari.max-lifetime")).isEqualTo("300000");
    }

    @Test
    void envVarsOverrideTheDefaults() throws IOException {
        PropertySourcesPropertyResolver r = resolver(Map.of(
                "AUTH_DB_POOL_SIZE", "15",
                "AUTH_DB_MIN_IDLE", "2",
                "AUTH_DB_CONNECTION_TIMEOUT_MS", "10000",
                "AUTH_DB_MAX_LIFETIME_MS", "600000"));
        assertThat(r.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("15");
        assertThat(r.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo("2");
        assertThat(r.getProperty("spring.datasource.hikari.connection-timeout")).isEqualTo("10000");
        assertThat(r.getProperty("spring.datasource.hikari.max-lifetime")).isEqualTo("600000");
    }

    @Test
    void otherServicesVariablesDoNotApply() throws IOException {
        PropertySourcesPropertyResolver r = resolver(Map.of(
                "OTHER_DB_POOL_SIZE", "99", "DB_POOL_SIZE", "99"));
        assertThat(r.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("5");
    }
}
