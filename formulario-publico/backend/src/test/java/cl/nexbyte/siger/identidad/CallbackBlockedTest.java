package cl.nexbyte.siger.identidad;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class CallbackBlockedTest {
    @Test void unapprovedCallbackStopsAuthorizationBeforeSendingCitizenToProvider() throws Exception {
        var properties = new ClaveUnicaProperties();
        var filter = new CallbackAvailabilityFilter(properties);
        var request = new MockHttpServletRequest("GET", "/oauth2/authorization/claveunica");
        request.setServletPath("/oauth2/authorization/claveunica");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (r, s) -> { throw new AssertionError("Should not authorize"); });
        assertThat(response.getStatus()).isEqualTo(503);
    }
}
