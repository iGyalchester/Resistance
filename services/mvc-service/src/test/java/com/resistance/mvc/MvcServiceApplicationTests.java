package com.resistance.mvc;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MvcServiceApplicationTests {

	@Value("${local.server.port}")
	private int port;

	@Test
	void contextLoads() {
	}

	/**
	 * "/" used to serve a course demo: an applicant registration form with
	 * no connection to the tracker, on the one URL a visitor is most likely
	 * to type. It now redirects to the dashboard, which for an anonymous
	 * caller means the security chain sends them on to /login.
	 */
	@Test
	void rootRedirectsRatherThanServingADemoForm() throws Exception {
		HttpResponse<Void> response = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER).build()
				.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).GET().build(),
						HttpResponse.BodyHandlers.discarding());

		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location").orElseThrow())
				.doesNotContain("applicant");
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
	 * What the qa profile turns on. The ALB terminates TLS and forwards
	 * plain HTTP, so without forward-headers-strategy every redirect to
	 * /login would send the browser to http:// - off the certificate and,
	 * on a HSTS domain, into an error page. Nested so the local build's
	 * "!MvcServiceApplicationTests" exclusion still covers it: like its
	 * parent it needs a real MySQL and runs in CI.
	 */
	@Nested
	@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = "server.forward-headers-strategy=native")
	class ForwardedHeaders {

		@Value("${local.server.port}")
		private int forwardedPort;

		@Test
		void redirectsHonourForwardedProto() throws Exception {
			HttpResponse<Void> response = HttpClient.newBuilder()
					.followRedirects(HttpClient.Redirect.NEVER).build()
					.send(HttpRequest.newBuilder(
									URI.create("http://localhost:" + forwardedPort + "/dashboard"))
							.header("X-Forwarded-Proto", "https")
							.GET().build(),
							HttpResponse.BodyHandlers.discarding());

			assertThat(response.statusCode()).isEqualTo(302);
			assertThat(response.headers().firstValue("Location").orElseThrow())
					.startsWith("https://");
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
