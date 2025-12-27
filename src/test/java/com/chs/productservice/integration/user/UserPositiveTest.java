package com.chs.productservice.integration.user;

import com.chs.productservice.integration.BaseIntegrationTest;
import com.chs.productservice.integration.UserTestData;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("User API - Positive Test Cases")
public class UserPositiveTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Should create user successfully with valid data")
    void shouldCreateUserSuccessfully() {
        String email = generateUniqueEmail();
        String password = "SecurePass123!";
        String firstName = "John";
        String lastName = "Doe";
        String userPayload = String.format("""
            {
                "email": "%s",
                "password": "%s",
                "firstName": "%s",
                "lastName": "%s"
            }
            """, email, password, firstName, lastName);

        given()
                .contentType(ContentType.JSON)
                .body(userPayload)
                .when().post(USER_ENDPOINT)
                .then()
                .statusCode(HttpStatus.CREATED.value())
                // response body
                .body("id", notNullValue())
                .body("email", equalTo(email))
                .body("firstName", equalTo(firstName))
                .body("lastName", equalTo(lastName))
                .body("accountCreated", notNullValue())
                .body("accountUpdated", notNullValue())
                .body("password", nullValue()) // 驗證密碼不在響應中（安全要求）
                .body("$", not(hasKey("password"))); // 驗證密碼不在響應中（安全要求）
    }

    @ParameterizedTest(name = "Should create user with {0}")
    @CsvSource(delimiter = '|', value = {
            "short names | A | B | ShortPass1!",
            "long names | VeryLongFirstNameWithManyCharacters | VeryLongLastNameWithManyCharacters | ComplexPassword123!@#",
            "names with spaces | John Paul | Van Der Berg | Pass word123!"
    })
    @DisplayName("Should create user with different valid input combinations")
    void shouldCreateUserWithVariousValidInputs(String scenario, String firstName, String lastName, String password) {

        String email = generateUniqueEmail();
        String userPayload = String.format("""
            {
                "email": "%s",
                "password": "%s",
                "firstName": "%s",
                "lastName": "%s"
            }
            """, email, password, firstName, lastName);

        given()
                .contentType(ContentType.JSON)
                .body(userPayload)
                .when().post(USER_ENDPOINT)
                .then().statusCode(HttpStatus.CREATED.value())
                .body("firstName", equalTo(firstName))
                .body("lastName", equalTo(lastName))
                .body("email", equalTo(email));
    }

    @Test
    @DisplayName("Should get user info by valid ID with authentication")
    void shouldGetUserByValidId() {

        String email = generateUniqueEmail();
        String password = "SecurePass123!";
        String firstName = "Jane";
        String lastName = "Smith";
        UserTestData userData = createUserAndGetData(email, password, firstName, lastName);

        given()
                .header("Authorization", userData.authHeader())
                .when().get(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.OK.value())
                .body("id", equalTo(userData.userId()))
                .body("email", equalTo(email))
                .body("firstName", equalTo(firstName))
                .body("lastName", equalTo(lastName))
                .body("password", nullValue());
    }

    @Test
    @DisplayName("Should update user info successfully")
    void shouldUpdateUserSuccessfully() {

        String email = generateUniqueEmail();
        String password = "SecurePass123!";
        UserTestData userData = createUserAndGetData(email, password, "Original", "Name");
        String updatePayload = """
            {
                "firstName": "Updated",
                "lastName": "User",
                "password": "NewSecurePass456!"
            }
            """;

        given()
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body(updatePayload)
                .when()
                .put(USER_ENDPOINT + "/" + userData.userId())
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("firstName", equalTo("Updated"))
                .body("lastName", equalTo("User"))
                .body("accountUpdated", notNullValue())
                .body("password", nullValue()); // 密碼不應該出現在響應中
    }

    @Test
    @DisplayName("Should update different fields independently")
    void shouldUpdateFieldsIndependently() {

        String email = generateUniqueEmail();
        String password = "SecurePass123!";
        UserTestData userData = createUserAndGetData(email, password, "Original", "Name");

        given() // 第一次更新：只更新 firstName
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "firstName": "UpdatedFirst",
                        "lastName": "Name",
                        "password": "SecurePass123!"
                    }
                    """)
                .when()
                .put(USER_ENDPOINT + "/" + userData.userId())
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("firstName", equalTo("UpdatedFirst"))
                .body("lastName", equalTo("Name"));

        given() // 第二次更新：只更新 lastName
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "firstName": "UpdatedFirst",
                        "lastName": "UpdatedLast",
                        "password": "SecurePass123!"
                    }
                    """)
                .when()
                .put(USER_ENDPOINT + "/" + userData.userId())
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("firstName", equalTo("UpdatedFirst"))
                .body("lastName", equalTo("UpdatedLast"));
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginWithValidCredentials() {
        String email = generateUniqueEmail();
        String password = "SecurePass123!";
        UserTestData userData = createUserAndGetData(email, password, "Test", "User");


        given() // 使用有效憑證訪問受保護的 endpoint
                .header("Authorization", userData.authHeader())
                .when()
                .get(USER_ENDPOINT + "/" + userData.userId())
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("email", equalTo(email));
    }

    @Test
    @DisplayName("Should access protected endpoints with valid authentication")
    void shouldAccessProtectedEndpointWithValidAuth() {
        String email = generateUniqueEmail();
        String password = "SecurePass123!";
        UserTestData userData = createUserAndGetData(email, password, "Test", "User");

        given() // 測試 GET 操作 - 應該成功
                .header("Authorization", userData.authHeader())
                .when()
                .get(USER_ENDPOINT + "/" + userData.userId())
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("id", equalTo(userData.userId()));

        given() // 測試 PUT 操作 - 應該成功
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "firstName": "Updated",
                        "lastName": "Name",
                        "password": "NewPass123!"
                    }
                    """)
                .when()
                .put(USER_ENDPOINT + "/" + userData.userId())
                .then()
                .statusCode(HttpStatus.OK.value())
                .body("firstName", equalTo("Updated"));
    }
}
