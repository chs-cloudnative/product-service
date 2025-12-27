package com.chs.productservice.integration.product;

import com.chs.productservice.integration.BaseIntegrationTest;
import com.chs.productservice.integration.UserTestData;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

@DisplayName("Product API - Edge Case Tests")
public class ProductEdgeCaseTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Should handle zero quantity")
    void shouldHandleZeroQuantity() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "John", "Doe");

        String productPayload = String.format("""
            {
                "sku": "%s",
                "name": "Zero Quantity Product",
                "description": "Test",
                "manufacturer": "Test",
                "quantity": 0
            }
            """, generateUniqueSku());

        given()
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body(productPayload)
                .when().post(PRODUCT_ENDPOINT)
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .body("quantity", equalTo(0));
    }


    @Test
    @DisplayName("Should not find deleted product")
    void shouldNotFindDeletedProduct() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "John", "Doe");
        String productId = createTestProduct(userData.authHeader(), generateUniqueSku(),
                "Product", "Description", "Manufacturer", 10);

        given() // 刪除產品
                .header("Authorization", userData.authHeader())
                .when().delete(PRODUCT_ENDPOINT + "/" + productId)
                .then().statusCode(HttpStatus.NO_CONTENT.value());

        given() // 查詢已刪除的產品應該返回 404
                .when().get(PRODUCT_ENDPOINT + "/" + productId)
                .then().statusCode(HttpStatus.NOT_FOUND.value());
    }


    @Test
    @DisplayName("Should respond within reasonable time")
    void shouldRespondWithinReasonableTime() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "John", "Doe");

        String productPayload = String.format("""
            {
                "sku": "%s",
                "name": "Performance Test Product",
                "description": "Testing response time",
                "manufacturer": "Test",
                "quantity": 100
            }
            """, generateUniqueSku());

        given()
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body(productPayload)
                .when().post(PRODUCT_ENDPOINT)
                .then()
                .statusCode(HttpStatus.CREATED.value())
                .time(lessThan(2000L)); // 響應時間應該 < 2 秒
    }
}
