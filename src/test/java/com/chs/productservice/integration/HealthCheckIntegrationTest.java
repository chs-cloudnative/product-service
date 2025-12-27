package com.chs.productservice.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;


@DisplayName("Health Check API Integration Tests")
public class HealthCheckIntegrationTest extends BaseIntegrationTest{

    @Test
    @DisplayName("Should return 200 OK with healthy status")
    void shouldReturnHealthyStatus() {
        given()
                .when()
                .get(HEALTH_ENDPOINT)
                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType(containsString("application/json")) // 可以容忍 "application/json;charset=UTF-8" 等變化
                .body("status", equalTo("OK"))
                .body("message", equalTo("Application is running"))
                .body("size()", greaterThan(0));
    }

    @Test
    @DisplayName("Should respond quickly (< 1 second)")
    void shouldRespondQuickly() {
        given()
                .when()
                .get(HEALTH_ENDPOINT)
                .then()
                .statusCode(HttpStatus.OK.value())
                .time(lessThan(1000L));
    }

    @Test
    @DisplayName("Should reject POST request")
    void shouldRejectPostRequest() {
        given()
                .when()
                .post(HEALTH_ENDPOINT)  // 嘗試 POST 請求
                .then()
                .statusCode(anyOf(
                        equalTo(HttpStatus.METHOD_NOT_ALLOWED.value()), // 405 Method Not Allowed
                        equalTo(HttpStatus.NOT_FOUND.value()) // 404 Not Found（取決於 Spring 配置）
                ));
    }

    @Test
    @DisplayName("Should not require authentication")
    void shouldNotRequireAuthentication() {
        given()
                .when()
                .get(HEALTH_ENDPOINT)
                .then()
                .statusCode(HttpStatus.OK.value()) // 200 OK，而不是 401 Unauthorized
                .body("status", equalTo("OK"));
    }
}
