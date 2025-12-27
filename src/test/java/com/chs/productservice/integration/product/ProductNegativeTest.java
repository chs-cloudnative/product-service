package com.chs.productservice.integration.product;

import com.chs.productservice.integration.BaseIntegrationTest;
import com.chs.productservice.integration.UserTestData;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static io.restassured.RestAssured.given;

@DisplayName("Product API - Negative Test Cases")
public class ProductNegativeTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Should reject product creation with missing required fields")
    void shouldRejectMissingRequiredFields() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "John", "Doe");

        String invalidPayload = """
            {
                "name": "Test Product",
                "description": "Test Description"
            }
            """;

        given()
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body(invalidPayload)
                .when().post(PRODUCT_ENDPOINT)
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }


    @Test
    @DisplayName("Should reject product creation without authentication")
    void shouldRejectWithoutAuthentication() {
        String productPayload = String.format("""
            {
                "sku": "%s",
                "name": "Test Product",
                "description": "Test Description",
                "manufacturer": "Test Manufacturer",
                "quantity": 10
            }
            """, generateUniqueSku());

        given()
                .contentType(ContentType.JSON)
                .body(productPayload)
                .when().post(PRODUCT_ENDPOINT)
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }


    @Test
    @DisplayName("Should reject updating other user's product")
    void shouldRejectUpdatingOtherUsersProduct() {
        // 用戶 1 創建產品
        UserTestData user1 = createUserAndGetData(generateUniqueEmail(), "Pass123!", "User", "One");
        String productId = createTestProduct(user1.authHeader(), generateUniqueSku(),
                "Product", "Description", "Manufacturer", 10);

        // 用戶 2 嘗試更新用戶 1 的產品
        UserTestData user2 = createUserAndGetData(generateUniqueEmail(), "Pass456!", "User", "Two");

        String updatePayload = """
            {
                "name": "Hacked Product",
                "description": "Hacked",
                "manufacturer": "Hacked",
                "quantity": 999
            }
            """;

        given()
                .header("Authorization", user2.authHeader())
                .contentType(ContentType.JSON)
                .body(updatePayload)
                .when().put(PRODUCT_ENDPOINT + "/" + productId)
                .then().statusCode(HttpStatus.NOT_FOUND.value());
    }
}
