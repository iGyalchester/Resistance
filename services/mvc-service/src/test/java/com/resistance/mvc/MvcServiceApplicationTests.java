package com.resistance.mvc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MvcServiceApplicationTests {

	@Value("${local.server.port}")
	private int port;

	@Test
	void contextLoads() {
	}

	private HttpResponse<String> fetch(String path, String... headers) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
		for (int i = 0; i < headers.length; i += 2) {
			request.header(headers[i], headers[i + 1]);
		}
		return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
				.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	/**
	 * The React shell is the whole UI now: "/" serves it, and so does any
	 * app route the browser might reload on (including an old server-rendered
	 * URL, which must not 404 for a bookmark). The build bundles the app
	 * through the frontend Maven profile, which is why this runs in CI only.
	 */
	@Test
	void rootAndAppRoutesServeTheShell() throws Exception {
		for (String path : new String[] {"/", "/dashboard", "/applications/42", "/applications/list", "/help"}) {
			HttpResponse<String> response = fetch(path);
			assertThat(response.statusCode()).as(path).isEqualTo(200);
			assertThat(response.body()).as(path).contains("<div id=\"root\">");
		}
	}

	@Test
	void missingAssetsAre404NotTheShell() throws Exception {
		assertThat(fetch("/assets/nope.js").statusCode()).isEqualTo(404);
		assertThat(fetch("/favicon.ico").statusCode()).isEqualTo(404);
	}

	@Test
	void apiStaysAuthenticatedWithJsonAnswers() throws Exception {
		HttpResponse<String> response = fetch("/api/applications");
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.body()).isEqualTo("{\"error\":\"unauthenticated\"}");
	}

	/** dev has no HSTS: it is plain http, and a stray header would poison localhost for a year. */
	@Test
	void noStrictTransportSecurityByDefault() throws Exception {
		HttpResponse<String> response = fetch("/actuator/health", "X-Forwarded-Proto", "https");
		assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
	}

	/**
	 * The AWS load balancer probes this anonymously through the real filter
	 * chain; it must answer 200, never redirect to /login.
	 */
	@Test
	void healthEndpointIsOpen() throws Exception {
		HttpResponse<String> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\"");
	}

	/**
	 * The admin rule through the real filter chain: anonymous callers get
	 * the API's JSON 401, a signed-in USER gets a JSON 403, and only an
	 * ADMIN reaches the controller. The roles are stamped straight onto the
	 * request (spring-security-test) because the OTP flow cannot be driven
	 * from a test without reading the mailbox.
	 */
	@Nested
	class AdminRule {

		@Autowired
		private WebApplicationContext context;

		private MockMvc mockMvc;

		@BeforeEach
		void setUp() {
			mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		}

		@Test
		void anonymousIs401Json() throws Exception {
			mockMvc.perform(get("/api/admin/overview"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error").value("unauthenticated"));
		}

		@Test
		void userIs403Json() throws Exception {
			mockMvc.perform(get("/api/admin/overview").with(user("boris@example.com").roles("USER")))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error").value("forbidden"));
		}

		@Test
		void adminIs200() throws Exception {
			mockMvc.perform(get("/api/admin/overview").with(user("ops@example.com").roles("USER", "ADMIN")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.accounts").isNumber())
					.andExpect(jsonPath("$.intakeEventsLast30Days.length()").value(30));
		}

		@Test
		void helpIsPublicAndOtherApiStaysAuthenticated() throws Exception {
			mockMvc.perform(get("/api/help")).andExpect(status().isOk());
			mockMvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
		}
	}

	/**
	 * What the qa profile turns on. The ALB terminates TLS and forwards
	 * plain HTTP, so without forward-headers-strategy the app would think
	 * every request is http: cookies would miss the Secure flag and HSTS
	 * would never be sent. With it, a request the balancer marks as https
	 * is secure to Spring Security, and the HSTS header (qa only, via
	 * tracker.security.hsts) goes out; a plain request still gets none.
	 * Nested so the local build's "!MvcServiceApplicationTests" exclusion
	 * still covers it: like its parent it needs a real MySQL and runs in CI.
	 */
	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = {"server.forward-headers-strategy=native", "tracker.security.hsts=true"})
	class ForwardedHeaders {

		@Value("${local.server.port}")
		private int forwardedPort;

		private HttpResponse<String> health(String... headers) throws Exception {
			HttpRequest.Builder request = HttpRequest.newBuilder(
					URI.create("http://localhost:" + forwardedPort + "/actuator/health")).GET();
			for (int i = 0; i < headers.length; i += 2) {
				request.header(headers[i], headers[i + 1]);
			}
			return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
		}

		@Test
		void forwardedHttpsGetsStrictTransportSecurity() throws Exception {
			HttpResponse<String> response = health("X-Forwarded-Proto", "https");
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.headers().firstValue("Strict-Transport-Security").orElseThrow())
					.contains("max-age=31536000").contains("includeSubDomains");
		}

		@Test
		void plainHttpGetsNone() throws Exception {
			assertThat(health().headers().firstValue("Strict-Transport-Security")).isEmpty();
		}
	}

	/**
	 * With spring.mail.host set, Boot registers MailHealthIndicator and every
	 * ALB probe opens an SMTP connection. The probe runs every 30 seconds per
	 * task and the target group requires a 200, so a single SES hiccup would
	 * mark every task DOWN at once - the ALB drains the service and ECS
	 * replaces the tasks, taking the tracker offline because *outbound email*
	 * is unwell.
	 *
	 * Port 1 on localhost is closed, so this is that outage: if the indicator
	 * ever comes back, health goes DOWN and this test fails. Nested so the
	 * local build's "!MvcServiceApplicationTests" exclusion still covers it.
	 */
	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = {"spring.mail.host=127.0.0.1", "spring.mail.port=1"})
	class UnreachableSmtp {

		@Value("${local.server.port}")
		private int smtpPort;

		@Test
		void healthIsUpWhenSmtpIsUnreachable() throws Exception {
			HttpResponse<String> response = HttpClient.newHttpClient().send(
					HttpRequest.newBuilder(URI.create(
							"http://localhost:" + smtpPort + "/actuator/health")).GET().build(),
					HttpResponse.BodyHandlers.ofString());

			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).contains("UP");
		}
	}

}
