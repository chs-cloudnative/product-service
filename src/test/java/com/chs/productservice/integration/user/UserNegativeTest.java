package com.chs.productservice.integration.user;

import com.chs.productservice.integration.BaseIntegrationTest;
import com.chs.productservice.integration.UserTestData;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.util.Base64;

import static io.restassured.RestAssured.given;

@DisplayName("User API - Negative Test Cases")
public class UserNegativeTest extends BaseIntegrationTest {

    @ParameterizedTest(name = "Should reject when {0}")
    @CsvSource(delimiter = '|', value = {
            "missing email | {\"password\":\"Pass123!\",\"firstName\":\"John\",\"lastName\":\"Doe\"}",
            "missing password | {\"email\":\"test@example.com\",\"firstName\":\"John\",\"lastName\":\"Doe\"}",
            "missing firstName | {\"email\":\"test@example.com\",\"password\":\"Pass123!\",\"lastName\":\"Doe\"}",
            "missing lastName | {\"email\":\"test@example.com\",\"password\":\"Pass123!\",\"firstName\":\"John\"}"
    })
    @DisplayName("Should reject user creation with missing required fields")
    void shouldRejectMissingRequiredFields(String scenario, String payload) {
        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post(USER_ENDPOINT)
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @ParameterizedTest(name = "Should reject invalid email: {0}")
    @ValueSource(strings = {
            "not-an-email",
            "missing-at-sign.com",
            "@no-local-part.com",
            "no-domain@",
            "spaces in@email.com",
            "double@@at.com"
    })
    @DisplayName("Should reject invalid email format")
    void shouldRejectInvalidEmailFormat(String invalidEmail) {
        String payload = String.format("""
            {
                "email": "%s",
                "password": "SecurePass123!",
                "firstName": "John",
                "lastName": "Doe"
            }
            """, invalidEmail);

        given()
                .contentType(ContentType.JSON)
                .body(payload)
                .when().post(USER_ENDPOINT)
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Should reject duplicate email")
    void shouldRejectDuplicateEmail() {
        String email = generateUniqueEmail();
        String userPayload = String.format("""
            {
                "email": "%s",
                "password": "SecurePass123!",
                "firstName": "John",
                "lastName": "Doe"
            }
            """, email);

        UserTestData user = createUserAndGetData(email, "SecurePass123!", "John", "Doe");

        given() // 第二次使用相同郵箱應該失敗
                .contentType(ContentType.JSON)
                .body(userPayload)
                .when().post(USER_ENDPOINT)
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Should reject updates with invalid data types")
    void shouldRejectInvalidDataTypes() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "SecurePass123!", "Test", "User");

        String invalidPayload = """
            {
                "firstName": {1,2,3},
                "lastName": "User",
                "password": "SecurePass123!"
            }
            """;

        given()
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body(invalidPayload)
                .when().put(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Should reject login with invalid credentials")
    void shouldRejectInvalidCredentials() {
        String email = generateUniqueEmail();
        UserTestData userData = createUserAndGetData(email, "CorrectPass123!", "John", "Doe");

        // 使用錯誤的密碼構建認證頭
        String wrongAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString((email + ":WrongPassword").getBytes());

        given() // 嘗試訪問，應該被拒絕
                .header("Authorization", wrongAuthHeader)
                .when().get(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("Should reject access without authentication token")
    void shouldRejectWithoutAuthToken() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "SecurePass123!", "John", "Doe");

        given() // 嘗試不提供認證頭訪問
                .when().get(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    @DisplayName("Should reject access to other user's info")
    void shouldRejectAccessToOtherUser() {
        // 創建第一個用戶
        String user1Email = generateUniqueEmail();
        String user1Password = "Pass123!";
        UserTestData user1Data = createUserAndGetData(user1Email, user1Password, "User", "One");

        // 創建第二個用戶
        String user2Email = generateUniqueEmail();
        String user2Password = "Pass456!";
        UserTestData user2Data = createUserAndGetData(user2Email, user2Password, "User", "Two");

        given() // User 2 嘗試訪問 User 1 的信息
                .header("Authorization", user2Data.authHeader())
                .when().get(USER_ENDPOINT + "/" + user1Data.userId())
                .then().statusCode(HttpStatus.FORBIDDEN.value());
    }

    @Test
    @DisplayName("Should return 404 for non-existent user")
    void shouldReturn404ForNonExistentUser() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "Test", "User");
        String nonExistentId = "00000000-0000-0000-0000-000000000000"; // 使用一個有效但不存在的 UUID

        given()
                .header("Authorization", userData.authHeader())
                .when().get(USER_ENDPOINT + "/" + nonExistentId)
                .then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Should return 404 when updating non-existent user")
    void shouldReturn404WhenUpdatingNonExistentUser() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "Test", "User");

        // 使用一個不存在的 UUID
        String nonExistentId = "00000000-0000-0000-0000-000000000000";
        String updatePayload = """
            {
                "firstName": "Updated",
                "lastName": "Name",
                "password": "NewPass123!"
            }
            """;

        given()
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body(updatePayload)
                .when().put(USER_ENDPOINT + "/" + nonExistentId)
                .then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Should reject wrong HTTP methods")
    void shouldRejectWrongHttpMethods() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "Test", "User");

        given() // POST 應該不支持在 /user/{id} 上
                .header("Authorization", userData.authHeader())
                .when().post(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());

        given() // DELETE 應該不支持（根據你的 Controller 沒有 DELETE 方法）
                .header("Authorization", userData.authHeader())
                .when().delete(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
    }

    @Test
    @DisplayName("Should return 404 for unsupported endpoints")
    void shouldReturn404ForUnsupportedEndpoints() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "Test", "User");

        given() // 訪問不存在的路徑
                .header("Authorization", userData.authHeader())
                .when().get("/v1/nonexistent")
                .then().statusCode(HttpStatus.NOT_FOUND.value());

        given() // 訪問錯誤的路徑結構
                .header("Authorization", userData.authHeader())
                .when().post("/v1/users/invalid/path")
                .then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Should reject empty request body")
    void shouldRejectEmptyRequestBody() {
        given()
                .contentType(ContentType.JSON)
                .body("")
                .when().post(USER_ENDPOINT)
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Should reject malformed JSON")
    void shouldRejectMalformedJson() {
        String malformedJson = "{invalid json structure";

        given()
                .contentType(ContentType.JSON)
                .body(malformedJson)
                .when().post(USER_ENDPOINT)
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }
}
