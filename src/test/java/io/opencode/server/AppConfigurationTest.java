package io.opencode.server;

import io.opencode.core.config.ConfigLoader;
import io.opencode.core.tool.util.ReferenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AppConfigurationTest {

    @Mock
    ConfigLoader configLoader;

    private final ReferenceService referenceService = new ReferenceService();

    @Test
    void corsWebFilterBean() {
        var appConfig = new AppConfiguration(configLoader, referenceService);
        assertNotNull(appConfig.corsWebFilter());
    }

    @Test
    void openCodeConfigBean() {
        var config = io.opencode.core.config.OpenCodeConfig.defaults(java.nio.file.Path.of("."));
        org.mockito.Mockito.when(configLoader.load(org.mockito.ArgumentMatchers.any()))
            .thenReturn(config);
        var appConfig = new AppConfiguration(configLoader, referenceService);
        assertEquals(config, appConfig.openCodeConfig());
    }
}
