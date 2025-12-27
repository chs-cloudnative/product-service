package com.chs.productservice.integration.product;

import com.chs.productservice.integration.BaseIntegrationTest;
import com.chs.productservice.integration.UserTestData;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@DisplayName("Product API - Positive Test Cases")
public class ProductPositiveTest extends BaseIntegrationTest {


    @Test
    @DisplayName("Should create product successfully with valid data")
    void shouldCreateProductSuccessfully() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "John", "Doe");
        String sku = generateUniqueSku();

        String productPayload = String.format("""
            {
                "sku": "%s",
                "name": "Test Product",
                "description": "Test Description",
                "manufacturer": "Test Manufacturer",
                "quantity": 10
            }
            """, sku);

        given()
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body(productPayload)
                .when().post(PRODUCT_ENDPOINT)
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .body("id", notNullValue())
                .body("sku", equalTo(sku))
                .body("name", equalTo("Test Product"))
                .body("quantity", equalTo(10));
    }


    @Test
    @DisplayName("Should get product by valid ID")
    void shouldGetProductByValidId() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "John", "Doe");
        String productId = createTestProduct(userData.authHeader(), generateUniqueSku(),
                "Test Product", "Description", "Manufacturer", 5);

        given()
                .when().get(PRODUCT_ENDPOINT + "/" + productId)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("id", equalTo(productId))
                .body("name", equalTo("Test Product"))
                .body("quantity", equalTo(5));
    }


    @Test
    @DisplayName("Should update product successfully")
    void shouldUpdateProductSuccessfully() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "John", "Doe");
        String productId = createTestProduct(userData.authHeader(), generateUniqueSku(),
                "Original Product", "Original Description", "Original Manufacturer", 10);

        String updatePayload = """
            {
                "name": "Updated Product",
                "description": "Updated Description",
                "manufacturer": "Updated Manufacturer",
                "quantity": 20
            }
            """;

        given()
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body(updatePayload)
                .when().put(PRODUCT_ENDPOINT + "/" + productId)
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("name", equalTo("Updated Product"))
                .body("quantity", equalTo(20));
    }
}
