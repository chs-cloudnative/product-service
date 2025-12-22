package com.chs.webapp.integration;

import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Base64;

import static io.restassured.RestAssured.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")

public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port; // from random port

    protected static final String BASE_PATH = "/v1";
    protected static final String USER_ENDPOINT = BASE_PATH + "/user";
    protected static final String PRODUCT_ENDPOINT = BASE_PATH + "/product";
    protected static final String HEALTH_ENDPOINT = "/health";

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        RestAssured.config = RestAssured.config()
                .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                        .defaultObjectMapperType(ObjectMapperType.JACKSON_2)); // Object <-> JSON

    }

    protected UserTestData createUserAndGetData(String email, String password, String firstName, String lastName) {
        String userPayload = String.format("""
            {
                "email": "%s",
                "password": "%s",
                "firstName": "%s",
                "lastName": "%s"
            }
            """, email, password, firstName, lastName);

        String userId = given()
                .contentType(ContentType.JSON)
                .body(userPayload)
                .when()
                .post(USER_ENDPOINT)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((email + ":" + password).getBytes());

        return new UserTestData(userId, email, authHeader);
    }

    protected String createTestProduct(String authHeader, String sku, String name, String description, String manufacturer, int quantity) {
        String productPayload = String.format("""
                {
                    "sku": "%s",
                    "name": "%s",
                    "description": "%s",
                    "manufacturer": "%s",
                    "quantity": %d
                }
                """, sku, name, description, manufacturer, quantity);

        return given()
                .header("Authorization", authHeader)
                .contentType(ContentType.JSON)
                .body(productPayload)
                .when()
                .post(PRODUCT_ENDPOINT)
                .then()
                .statusCode(201) // if not 201 -> Assertion Error
                .extract()
                .path("id");
    }

    protected String generateUniqueEmail() {
        return "test" + System.currentTimeMillis() + "@example.com";
    }

    protected String generateUniqueSku() {
        return "SKU" + System.currentTimeMillis();
    }


}
