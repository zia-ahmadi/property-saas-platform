package com.example.property_core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.example.property_core.auth.AuthRepository;
import com.example.property_core.platformadmin.PlatformAdminService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
		"app.platform-admin.allowed-emails=platform-admin-test@example.invalid",
		"app.platform-admin.bootstrap-token=test-bootstrap-token-with-more-than-32-characters"
})
class PropertyCoreApplicationTests {
	@Autowired
	private WebApplicationContext applicationContext;
	@Autowired
	private AuthRepository authRepository;
	@Autowired
	private PlatformAdminService platformAdminService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@Test
	void contextLoads() {
	}

	@Test
	void unauthenticatedApiRequestsReturnProblemDetails() throws Exception {
		mockMvc.perform(get("/api/v1/properties"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.instance").value("/api/v1/properties"));
	}

	@Test
	void frontendAuthPreflightAllowsConfiguredOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/auth/register")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers",
								"content-type,x-platform-admin-bootstrap-token"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().string("Access-Control-Allow-Methods",
						org.hamcrest.Matchers.containsString("POST")))
				.andExpect(header().string("Access-Control-Allow-Headers",
						org.hamcrest.Matchers.containsString("x-platform-admin-bootstrap-token")));
	}

	@Test
	void platformAdminApiRequiresPlatformAdminRole() throws Exception {
		mockMvc.perform(get("/api/v1/platform-admin/organizations")
						.with(SecurityMockMvcRequestPostProcessors.user("organization-admin").roles("ADMIN")))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/platform-admin/organizations")
						.with(SecurityMockMvcRequestPostProcessors.user("manager").roles("MANAGER")))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/platform-admin/organizations")
						.with(SecurityMockMvcRequestPostProcessors.user("platform-admin").roles("PLATFORM_ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());

		mockMvc.perform(get("/api/v1/platform-admin/users")
						.with(SecurityMockMvcRequestPostProcessors.user("platform-admin").roles("PLATFORM_ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());
	}

	@Test
	void platformAdminBootstrapRequiresAllowlistedEmail() throws Exception {
		mockMvc.perform(post("/api/v1/platform-admin/bootstrap")
						.header("X-Platform-Admin-Bootstrap-Token", "unused-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"not-allowlisted@example.com\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@Transactional
	void allowlistedAccountGetsPlatformRoleOnlyAfterTokenBootstrap() {
		String email = "platform-admin-test@example.invalid";
		UUID organizationId = authRepository.createOrganization("Platform Admin Test");
		UUID userId = authRepository.createUser(
				organizationId, email, "test-password-hash", "Platform Admin Test");
		authRepository.assignRole(organizationId, userId, "ADMIN");

		platformAdminService.bootstrap(email, "test-bootstrap-token-with-more-than-32-characters");

		assertTrue(authRepository.findByEmail(email)
				.orElseThrow()
				.roles()
				.contains("PLATFORM_ADMIN"));
	}

	@Test
	@Transactional
	void frontendRegistrationAndLoginContractWorksEndToEnd() throws Exception {
		String email = "auth-flow-" + UUID.randomUUID() + "@example.invalid";
		String password = "InitialPassword123";
		String registration = """
				{
				  "organizationName": "Auth Flow Test",
				  "email": "%s",
				  "password": "%s",
				  "fullName": "Auth Flow Test"
				}
				""".formatted(email, password);

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registration))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.roles[0]").value("ADMIN"));

		String login = """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
		String token = new ObjectMapper().readTree(mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(login))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString()).get("accessToken").asText();

		mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.tenantId").isNotEmpty())
				.andExpect(jsonPath("$.roles[0]").value("ADMIN"));
	}
}
