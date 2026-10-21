package io.github.luisr96.whathappenedhere.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS for the static frontend on another origin. Endpoint URLs carry valid parameters; the
 * detail id is unknown (404), which still must carry CORS headers so the browser can read the error.
 */
class CorsTest {

    private static final String PAGES = "https://luisr96.github.io";
    private static final String OTHER = "https://luisr96.example.org";
    private static final String EVIL = "https://evil.example.com";

    @Nested
    @AutoConfigureMockMvc
    @TestPropertySource(properties = "app.cors.allowed-origins=" + PAGES + "," + OTHER)
    class Configured extends PostgisIntegrationTest {

        @Autowired
        MockMvc mvc;

        @ParameterizedTest
        @ValueSource(strings = {
                "/events?bbox=-180,-90,180,90",
                "/events/Q0:BORN",
                "/places/nearby?lat=0&lon=0&radius=1000",
                "/stats/timeline?bbox=-180,-90,180,90", "/stats/summary"})
        void allowedOriginIsEchoedOnGet(String url) throws Exception {
            mvc.perform(get(url).header(HttpHeaders.ORIGIN, PAGES))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PAGES))
                    .andExpect(header().stringValues(HttpHeaders.VARY, hasItem(
                            containsString("Origin"))))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }

        @Test
        void secondConfiguredOriginIsEchoedExactly() throws Exception {
            mvc.perform(get("/events?bbox=-180,-90,180,90").header(HttpHeaders.ORIGIN, OTHER))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, OTHER));
        }

        @ParameterizedTest
        @ValueSource(strings = {"/events", "/events/Q0:BORN", "/places/nearby", "/stats/timeline", "/stats/summary"})
        void preflightForGetIsAllowed(String url) throws Exception {
            mvc.perform(options(url)
                            .header(HttpHeaders.ORIGIN, PAGES)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, PAGES))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, not(containsString("POST"))))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, not(containsString("PUT"))))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, not(containsString("DELETE"))))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        }

        @Test
        void preflightForPostIsRejected() throws Exception {
            mvc.perform(options("/events")
                            .header(HttpHeaders.ORIGIN, PAGES)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void disallowedOriginGetsNoAllowOrigin() throws Exception {
            mvc.perform(get("/events?bbox=-180,-90,180,90").header(HttpHeaders.ORIGIN, EVIL))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void disallowedOriginPreflightIsRejected() throws Exception {
            mvc.perform(options("/events")
                            .header(HttpHeaders.ORIGIN, EVIL)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void originPrefixIsNotEnough() throws Exception {
            mvc.perform(get("/events?bbox=-180,-90,180,90").header(HttpHeaders.ORIGIN, PAGES + ".evil.com"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void sameOriginRequestStillWorks() throws Exception {
            mvc.perform(get("/events?bbox=-180,-90,180,90"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Nested
    @AutoConfigureMockMvc
    class Unconfigured extends PostgisIntegrationTest {

        @Autowired
        MockMvc mvc;

        @Test
        void crossOriginGetGetsNoAllowOrigin() throws Exception {
            mvc.perform(get("/events?bbox=-180,-90,180,90").header(HttpHeaders.ORIGIN, PAGES))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void crossOriginPreflightGetsNoAllowOrigin() throws Exception {
            mvc.perform(options("/events")
                            .header(HttpHeaders.ORIGIN, PAGES)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }
}
