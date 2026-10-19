package io.github.luisr96.whathappenedhere.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * The frontend builds its types from the committed {@code frontend/openapi.json}; this keeps that
 * file equal to what the API really serves.
 */
@AutoConfigureMockMvc
class OpenApiContractTest extends PostgisIntegrationTest {

    // Gradle runs api tests from api/, so the frontend's copy is one level up.
    private static final Path CONTRACT = Path.of("..", "frontend", "openapi.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    @Test
    void committedContractMatchesGeneratedApiDocs() throws Exception {
        String served = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode generated = MAPPER.readTree(served);
        if (Boolean.getBoolean("updateOpenApi")) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, MAPPER.writer(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsString(generated) + "\n", StandardCharsets.UTF_8);
        }
        JsonNode committed = Files.exists(CONTRACT)
                ? MAPPER.readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8))
                : null;
        assertEquals(generated, committed, "frontend/openapi.json is out of date with the API. "
                + "Regenerate it with: ./gradlew :api:test --tests '*OpenApiContractTest' "
                + "-PupdateOpenApi, then commit the file.");
    }
}
