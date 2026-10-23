package cl.nexbyte.siger.identidad;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class SecurityConfiguration {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SecurityConfiguration.class);
    public static final String AUTHORIZE = "https://accounts.claveunica.gob.cl/openid/authorize/";
    public static final String TOKEN = "https://accounts.claveunica.gob.cl/openid/token/";
    public static final String USERINFO = "https://accounts.claveunica.gob.cl/openid/userinfo/";
    public static final String ISSUER = "https://accounts.claveunica.gob.cl/openid";
    public static final String JWKS = "https://accounts.claveunica.gob.cl/openid/jwks";

    @Bean
    ClientRegistrationRepository registrations(ClaveUnicaProperties p) {
        var registration = ClientRegistration.withRegistrationId("claveunica")
                .clientName("Defensoría de la Niñez").clientId(p.getClientId()).clientSecret(p.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(false).build())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(p.getRedirectUri().toString()).scope("openid", "run", "name")
                .authorizationUri(AUTHORIZE).tokenUri(TOKEN).userInfoUri(USERINFO)
                .userNameAttributeName("sub").issuerUri(ISSUER).jwkSetUri(JWKS).build();
        return new InMemoryClientRegistrationRepository(registration);
    }

    static JdkClientHttpRequestFactory requestFactory() {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    @Bean
    RestClientAuthorizationCodeTokenResponseClient tokenClient() {
        var client = new RestClientAuthorizationCodeTokenResponseClient();
        client.addParametersConverter(request -> {
            var parameters = new LinkedMultiValueMap<String, String>();
            parameters.add("state", request.getAuthorizationExchange().getAuthorizationRequest().getState());
            return parameters;
        });
        client.setRestClient(RestClient.builder().requestFactory(requestFactory())
                .configureMessageConverters(converters -> converters.disableDefaults()
                        .addCustomConverter(new FormHttpMessageConverter()).addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler()).build());
        return client;
    }

    @Bean
    org.springframework.security.oauth2.jwt.JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(ClaveUnicaProperties properties) {
        return new ClaveUnicaJwtDecoderFactory(properties);
    }

    @Bean
    ClaveUnicaOidcUserService oidcUserService() {
        var rest = new RestTemplate(requestFactory());
        rest.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        return new ClaveUnicaOidcUserService(rest);
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ClientRegistrationRepository registrations,
            RestClientAuthorizationCodeTokenResponseClient tokenClient, ClaveUnicaOidcUserService userService,
            ClaveUnicaProperties properties) throws Exception {
        var contextRepository = new HttpSessionSecurityContextRepository();
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(builder -> builder.scopes(new java.util.LinkedHashSet<>(java.util.List.of("openid", "run", "name"))));
        if (properties.getEnvironment() == ClaveUnicaProperties.Stage.PRODUCTION) {
            http.redirectToHttps(org.springframework.security.config.Customizer.withDefaults());
        }
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg", "/api/session", "/api/csrf",
                                "/oauth2/authorization/claveunica", "/redirect", "/logout", "/auth/session-expired", "/error").permitAll()
                        .requestMatchers("/api/nna/**").permitAll()
                        .requestMatchers("/api/comunicaciones/**", "/api/comunicaciones", "/api/identidad/atributos").hasRole("CIUDADANO").anyRequest().denyAll())
                .requestCache(cache -> cache.disable())
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self' https://accounts.claveunica.gob.cl"))
                        .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("application/problem+json");
                    response.getWriter().write("{\"title\":\"Sesión requerida o expirada\",\"status\":401}");
                }))
                .oauth2Login(oauth -> oauth.clientRegistrationRepository(registrations)
                        .authorizationEndpoint(authorization -> authorization.authorizationRequestResolver(resolver))
                        .authorizedClientRepository(new DiscardingAuthorizedClientRepository())
                        .redirectionEndpoint(callback -> callback.baseUri("/redirect"))
                        .tokenEndpoint(token -> token.accessTokenResponseClient(tokenClient))
                        .userInfoEndpoint(user -> user.oidcUserService(userService))
                        .successHandler((request, response, authentication) -> {
                            var oidcUser = (OidcUser) authentication.getPrincipal();
                            var identity = IdentidadVerificada.from(oidcUser.getUserInfo().getClaims());
                            var context = SecurityContextHolder.createEmptyContext();
                            var citizen = UsernamePasswordAuthenticationToken.authenticated(identity, null,
                                    java.util.List.of(new SimpleGrantedAuthority("ROLE_CIUDADANO")));
                            citizen.setDetails(EvidenciaAutenticacion.de(identity));
                            context.setAuthentication(citizen);
                            SecurityContextHolder.setContext(context);
                            contextRepository.saveContext(context, request, response);
                            LOGGER.info("CU_AUTHENTICATION_COMPLETED environment={} source=userinfo", properties.getEnvironment());
                            response.sendRedirect(properties.getPublicOrigin().toString());
                        })
                        .failureHandler((request, response, exception) -> {
                            String errorCode = exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException error
                                    ? error.getError().getErrorCode() : "authentication_failed";
                            if (errorCode == null || !errorCode.matches("[A-Za-z0-9_-]{1,80}")) errorCode = "authentication_failed";
                            LOGGER.warn("CU_AUTHENTICATION_FAILED environment={} error={}", properties.getEnvironment(), errorCode);
                            if (request.getSession(false) != null) request.getSession(false).invalidate();
                            SecurityContextHolder.clearContext();
                            if (exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauthError
                                    && java.util.Set.of("authorization_request_not_found", "invalid_state_parameter")
                                            .contains(oauthError.getError().getErrorCode())) {
                                response.sendRedirect(properties.getPublicOrigin() + "/?auth=failed");
                                return;
                            }
                            // Clean the provider session as well when login/identity validation fails.
                            response.sendRedirect(ProviderLogout.url(properties));
                        }))
                .logout(logout -> logout.logoutUrl("/auth/logout").invalidateHttpSession(true).clearAuthentication(true)
                        .deleteCookies("SIGERSESSION")
                        .logoutSuccessHandler((request, response, authentication) -> response.sendRedirect(ProviderLogout.url(properties))))
                .addFilterBefore(new CallbackAvailabilityFilter(properties), OAuth2AuthorizationRequestRedirectFilter.class);
        return http.build(); // CSRF remains enabled, including logout and authenticated POSTs.
    }
}
