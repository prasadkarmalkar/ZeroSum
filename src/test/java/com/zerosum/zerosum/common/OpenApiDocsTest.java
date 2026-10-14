package com.zerosum.zerosum.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Keeps the Swagger docs usable without reading code: every operation has a summary and description, every
 * error response uses the ErrorResponse schema with at least one example, and every schema field has an example.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void everyOperationIsFullyDocumented() throws Exception {
		JsonNode docs = jsonMapper.readTree(mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString());
		List<String> problems = new ArrayList<>();

		for (Map.Entry<String, JsonNode> path : docs.get("paths").properties()) {
			for (Map.Entry<String, JsonNode> op : path.getValue().properties()) {
				String where = op.getKey().toUpperCase() + " " + path.getKey();
				JsonNode operation = op.getValue();
				if (!operation.hasNonNull("summary")) problems.add(where + ": no summary");
				if (!operation.hasNonNull("description")) problems.add(where + ": no description");
				if (operation.get("tags") == null) problems.add(where + ": no tag");

				for (Map.Entry<String, JsonNode> response : operation.get("responses").properties()) {
					String status = response.getKey();
					JsonNode media = response.getValue().path("content").path("application/problem+json");
					if (status.startsWith("4") || status.startsWith("5")) {
						if (!media.path("schema").path("$ref").asString("").endsWith("/ErrorResponse")) {
							problems.add(where + " " + status + ": error schema is not ErrorResponse");
						}
						if (media.path("examples").isEmpty()) {
							problems.add(where + " " + status + ": no error example (description names no errorCode?)");
						}
					}
				}
			}
		}

		for (Map.Entry<String, JsonNode> schema : docs.path("components").path("schemas").properties()) {
			if (schema.getKey().equals("ProblemDetail")) continue;
			for (Map.Entry<String, JsonNode> field : schema.getValue().path("properties").properties()) {
				JsonNode f = field.getValue();
				boolean documented = f.has("example") || f.has("$ref") || f.has("items") || f.has("enum")
						|| "date-time".equals(f.path("format").asString(""));
				if (!documented) problems.add("schema " + schema.getKey() + "." + field.getKey() + ": no example");
			}
		}

		assertThat(problems).isEmpty();
	}

}
