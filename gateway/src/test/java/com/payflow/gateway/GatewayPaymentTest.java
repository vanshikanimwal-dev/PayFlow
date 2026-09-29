package com.payflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayPaymentTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createIsIdempotentAndPayPageCaptures() throws Exception {
        String body = """
                {"reference":"tx-1","amountMinor":50000,"method":"UPI","callbackUrl":"http://127.0.0.1:9/hook"}
                """;
        MvcResult created = mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String paymentId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.paymentId");

        mockMvc.perform(post("/v1/payments")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId));

        mockMvc.perform(get("/v1/payments").param("reference", "tx-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId));

        mockMvc.perform(post("/pay/" + paymentId + "/complete"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/payments/" + paymentId))
                .andExpect(jsonPath("$.status").value("CAPTURED"));
        assertThat(paymentId).startsWith("pi_");
    }
}
