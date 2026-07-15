package com.shivang.crm.modules.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.integration.service.impl.SimpleTemplateResolver;

class SimpleTemplateResolverTest {

    @Test
    void resolvesSimplePlaceholders() {
        SimpleTemplateResolver resolver = new SimpleTemplateResolver();

        String resolved = resolver.resolve(
            "Hello {{entity.firstName}} from {{tenant.name}}",
            Map.of(
                "entity", Map.of("firstName", "Jane"),
                "tenant", Map.of("name", "Acme")
            )
        );

        assertThat(resolved).isEqualTo("Hello Jane from Acme");
    }
}
