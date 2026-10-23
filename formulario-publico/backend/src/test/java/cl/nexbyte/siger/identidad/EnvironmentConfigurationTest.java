package cl.nexbyte.siger.identidad;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ClaveUnicaProperties.class)
    static class PropertiesOnly { }

    ApplicationContextRunner configured(String stage) {
        return new ApplicationContextRunner().withUserConfiguration(PropertiesOnly.class).withPropertyValues(
                "siger.claveunica.environment=" + stage,
                "siger.claveunica.client-id=test-client", "siger.claveunica.client-secret=test-secret",
                "siger.claveunica.redirect-uri=https://siger.ddn.gob.cl/redirect",
                "siger.claveunica.logout-uri=https://siger.ddn.gob.cl/logout",
                "siger.claveunica.public-origin=https://siger.ddn.gob.cl",
                "siger.claveunica.callback-approved=true", "server.servlet.session.cookie.secure=true");
    }
    @Test void sameIntegrationAndOfficialEndpointsForAllEnvironments() {
        for (String stage : new String[]{"SANDBOX", "QA", "PRODUCTION"}) {
            configured(stage).run(context -> {
                assertThat(context).hasNotFailed();
                var properties = context.getBean(ClaveUnicaProperties.class);
                var registration = new SecurityConfiguration().registrations(properties).findByRegistrationId("claveunica");
                assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo(SecurityConfiguration.TOKEN);
                assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo(SecurityConfiguration.USERINFO);
                assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid", "run", "name");
                assertThat(properties.getEnvironment().name()).isEqualTo(stage);
            });
        }
    }
    @Test void productionRejectsInsecureTransportCookieAndUnapprovedCallback() {
        for (String invalid : new String[]{"siger.claveunica.public-origin=http://localhost:5173",
                "siger.claveunica.redirect-uri=https://example.com/redirect",
                "server.servlet.session.cookie.secure=false", "siger.claveunica.callback-approved=false"}) {
            configured("PRODUCTION").withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
        }
    }
    @Test void rejectsUnregisteredUriShapeAndMissingSecret() {
        for (String invalid : new String[]{"siger.claveunica.redirect-uri=https://siger.ddn.gob.cl/redirect?extra=1",
                "siger.claveunica.redirect-uri=https://127.0.0.1/redirect",
                "siger.claveunica.logout-uri=https://siger.ddn.gob.cl/logout#fragment",
                "siger.claveunica.client-secret="}) {
            configured("SANDBOX").withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
        }
    }
}
