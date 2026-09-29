package com.payflow.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payflow.support.ProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(
        controllers = ProbeController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
        })
class ErrorContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void echoesASafeCorrelationIdOnSuccessAndInTheLogContext() throws Exception {
        mockMvc.perform(get("/api/v1/_probe/correlation").header(CorrelationIds.HEADER, "req-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIds.HEADER, "req-123"))
                .andExpect(jsonPath("$.correlationId").value("req-123"));
    }

    @Test
    void generatesACorrelationIdWhenTheHeaderIsMissingOrUnsafe() throws Exception {
        MvcResult missing = mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andReturn();

        String generated = missing.getResponse().getHeader(CorrelationIds.HEADER);
        assertThat(generated).isNotBlank();
        assertThat(missing.getResponse().getContentAsString()).contains(generated);

        mockMvc.perform(get("/api/v1/does-not-exist").header(CorrelationIds.HEADER, "bad id"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(CorrelationIds.HEADER, not("bad id")))
                .andExpect(jsonPath("$.correlationId").value(not("bad id")));
    }

    @Test
    void validationErrorsUseTheStandardBody() throws Exception {
        mockMvc.perform(post("/api/v1/_probe/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("name")));
    }

    @Test
    void malformedJsonDoesNotLeakParserDetails() throws Exception {
        mockMvc.perform(post("/api/v1/_probe/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request body is invalid"))
                .andExpect(content().string(not(containsString("JsonParseException"))));
    }

    @Test
    void businessErrorsKeepTheirCodeAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/_probe/missing-funds"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.message").value("Wallet balance is too low"));
    }

    @Test
    void unexpectedErrorsHideTheExceptionMessage() throws Exception {
        mockMvc.perform(get("/api/v1/_probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret"))));
    }
}
