package io.kestra.controller;

import io.kestra.controller.config.ControllerConfiguration;
import io.kestra.controller.config.GrpcConfiguration;
import io.kestra.controller.config.GrpcTlsConfiguration;
import io.kestra.controller.config.GrpcTlsConfiguration.ClientAuth;
import io.kestra.controller.config.GrpcTlsConfiguration.KeyStoreConfig;
import io.kestra.controller.config.GrpcTlsConfiguration.TrustStoreConfig;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.micronaut.context.event.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultControllerTlsTest {

    private static final String TEST_KEYSTORE = testResource("tls/server-keystore.p12");
    private static final String KEYSTORE_PASSWORD = "testpass";

    private static String testResource(String name) {
        return DefaultControllerTlsTest.class.getClassLoader().getResource(name).getPath();
    }

    @Test
    void shouldFailWhenTlsEnabledWithoutKeyStore() {
        // Given
        var tlsConfig = new GrpcTlsConfiguration(true, null, null, ClientAuth.NONE, false);
        var controller = createController(tlsConfig);

        // When/Then
        assertThatThrownBy(controller::start)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("key-store is required");
    }

    @ParameterizedTest
    @EnumSource(value = ClientAuth.class, names = {"OPTIONAL", "REQUIRE"})
    void shouldFailWhenClientAuthWithoutTrustStore(ClientAuth clientAuth) {
        // Given
        var keyStore = new KeyStoreConfig(TEST_KEYSTORE, "PKCS12", KEYSTORE_PASSWORD, null);
        var tlsConfig = new GrpcTlsConfiguration(true, keyStore, null, clientAuth, false);
        var controller = createController(tlsConfig);

        // When/Then
        assertThatThrownBy(controller::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("trust-store is required")
            .hasMessageContaining(clientAuth.name());
    }

    @SuppressWarnings("unchecked")
    private DefaultController createController(GrpcTlsConfiguration tlsConfig) {
        var grpcConfig = new GrpcConfiguration(false, Integer.MAX_VALUE);
        var controllerConfig = new ControllerConfiguration(
            9096,
            Duration.ofMinutes(5),
            Duration.ofSeconds(10)
        );
        var eventPublisher = (ApplicationEventPublisher<ServiceStateChangeEvent>) event -> {};
        return new DefaultController(List.of(), grpcConfig, tlsConfig, controllerConfig, eventPublisher);
    }
}
