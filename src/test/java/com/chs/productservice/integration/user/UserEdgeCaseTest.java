package com.chs.productservice.integration.user;

import com.chs.productservice.integration.BaseIntegrationTest;
import com.chs.productservice.integration.UserTestData;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("User API - Edge Case Tests")
public class UserEdgeCaseTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Should handle minimum string lengths")
    void shouldHandleMinimumStringLengths() {
        String email = generateUniqueEmail();
        String userPayload = String.format("""
            {
                "email": "%s",
                "password": "SecurePass123!",
                "firstName": "A",
                "lastName": "B"
            }
            """, email);

        given()
                .contentType(ContentType.JSON)
                .body(userPayload)
                .when().post(USER_ENDPOINT)
                .then().statusCode(HttpStatus.CREATED.value());
    }

    @Test
    @DisplayName("Should handle maximum string lengths")
    void shouldHandleMaximumStringLengths() {
        String email = generateUniqueEmail();
        String veryLongString = "A".repeat(255); // 創建 255 字符的超長字串

        String userPayload = String.format("""
            {
                "email": "%s",
                "password": "SecurePass123!",
                "firstName": "%s",
                "lastName": "%s"
            }
            """, email, veryLongString, veryLongString);

        given()
                .contentType(ContentType.JSON)
                .body(userPayload)
                .when().post(USER_ENDPOINT)
                .then().statusCode(HttpStatus.CREATED.value());
    }

    @ParameterizedTest(name = "Should handle special characters: {0}")
    @ValueSource(strings = {
            "O'Brien",
            "José",
            "François",
            "李明",
            "Müller",
            "Test-Name",
            "Test.Name"
    })
    @DisplayName("Should handle special characters in names")
    void shouldHandleSpecialCharactersInNames(String name) {
        String email = generateUniqueEmail();
        String userPayload = String.format("""
            {
                "email": "%s",
                "password": "SecurePass123!",
                "firstName": "%s",
                "lastName": "TestLast"
            }
            """, email, name);

        given()
                .contentType(ContentType.JSON)
                .body(userPayload)
                .when().post(USER_ENDPOINT)
                .then().statusCode(HttpStatus.CREATED.value());
    }

    @Test
    @DisplayName("Should return 400 for invalid UUID format")
    void shouldReturn400ForInvalidUuidFormat() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "Test", "User");
        String invalidId = "not-a-valid-uuid"; // 使用無效的 UUID 格式

        given()
                .header("Authorization", userData.authHeader())
                .when().get(USER_ENDPOINT + "/" + invalidId)
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Should reject empty strings for required fields")
    void shouldRejectEmptyStrings() {
        String email = generateUniqueEmail();
        String userPayload = String.format("""
            {
                "email": "%s",
                "password": "SecurePass123!",
                "firstName": "",
                "lastName": "Doe"
            }
            """, email);

        given()
                .contentType(ContentType.JSON)
                .body(userPayload)
                .when().post(USER_ENDPOINT)
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Should respond within reasonable time")
    void shouldRespondWithinReasonableTime() {
        String email = generateUniqueEmail();
        String userPayload = String.format("""
            {
                "email": "%s",
                "password": "SecurePass123!",
                "firstName": "John",
                "lastName": "Doe"
            }
            """, email);

        given()
                .contentType(ContentType.JSON)
                .body(userPayload)
                .when().post(USER_ENDPOINT)
                .then().statusCode(HttpStatus.CREATED.value())
                .time(lessThan(2000L)); // 驗證響應時間小於 2000 毫秒
    }

    @Test
    @DisplayName("Should handle concurrent requests")
    void shouldHandleConcurrentRequests() throws InterruptedException {
        int concurrentRequests = 5;

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests)) { // 創建線程池
            CountDownLatch latch = new CountDownLatch(concurrentRequests); // 用於等待所有線程完成
            AtomicInteger successCount = new AtomicInteger(0); // 線程安全的計數器

            for (int i = 0; i < concurrentRequests; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        String userPayload = String.format("""
                        {
                            "email": "%s",
                            "password": "SecurePass123!",
                            "firstName": "Concurrent",
                            "lastName": "User"
                        }
                        """, index + generateUniqueEmail());

                        int statusCode = given()
                                .contentType(ContentType.JSON)
                                .body(userPayload)
                                .when().post(USER_ENDPOINT)
                                .then().extract().statusCode();

                        if (statusCode == HttpStatus.CREATED.value()) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(10, TimeUnit.SECONDS);
            assertTrue(completed, "Not all tasks finished within the timeout");

            boolean success80percent = successCount.get() >= concurrentRequests * 0.8;
            assertTrue(success80percent, "At least 80% should succeed. " + "Success count: " + successCount.get() + "/" + concurrentRequests);
        } // 這裡自動 shutdown executor
    }

    @Test
    @DisplayName("Should handle GET requests quickly")
    void shouldHandleGetRequestsQuickly() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "Pass123!", "Test", "User");

        given()
                .header("Authorization", userData.authHeader())
                .when().get(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.OK.value())
                .time(lessThan(1000L)); // 查詢應該在 1 秒內完成
    }

    @Test
    @DisplayName("Should persist created data correctly")
    void shouldPersistCreatedData() {
        String email = generateUniqueEmail();
        String password = "SecurePass123!";
        String firstName = "Persist";
        String lastName = "Test";

        UserTestData userData = createUserAndGetData(email, password, firstName, lastName);

        given() // 查詢剛創建的用戶，驗證數據持久化
                .header("Authorization", userData.authHeader())
                .when().get(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.OK.value())
                .body("email", equalTo(email))
                .body("firstName", equalTo(firstName))
                .body("lastName", equalTo(lastName))
                .body("id", equalTo(userData.userId()))
                .body("password", nullValue());
    }

    @Test
    @DisplayName("Should not affect unmodified fields during update")
    void shouldNotAffectUnmodifiedFields() {
        String email = generateUniqueEmail();
        String password = "SecurePass123!";
        String originalFirstName = "Original";
        String originalLastName = "Name";
        UserTestData userData = createUserAndGetData(email, password, originalFirstName, originalLastName);

        String updatePayload = """
            {
                "firstName": "UpdatedFirst",
                "lastName": "Name",
                "password": "SecurePass123!"
            }
        """;

        given() // 只更新 firstName，其他字段保持不變
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body(updatePayload)
                .when().put(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.OK.value())
                .body("email", equalTo(email)) // email 不應該改變
                .body("lastName", equalTo(originalLastName)); // lastName 不應該改變
    }

    @Test
    @DisplayName("Should protect system-managed fields from user modification")
    void shouldProtectSystemManagedFields() {
        UserTestData userData = createUserAndGetData(generateUniqueEmail(), "SecurePass123!", "Test", "User");

        String maliciousPayload = """
            {
                "firstName": "Updated",
                "lastName": "User",
                "password": "SecurePass123!",
                "accountCreated": "2020-01-01T00:00:00Z",
                "id": "99999999-9999-9999-9999-999999999999"
            }
            """;

        given() // 嘗試修改系統管理字段（惡意請求）
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body(maliciousPayload)
                .when().put(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    @DisplayName("Should be able to login with new password after update")
    void shouldLoginWithNewPasswordAfterUpdate() {
        String email = generateUniqueEmail();
        String oldPassword = "OldPass123!";
        String newPassword = "NewPass456!";
        String updatePayload = String.format("""
                {
                    "password": "%s"
                }
                """, newPassword);

        UserTestData userData = createUserAndGetData(email, oldPassword, "Test", "User");

        given() // 更新密碼
                .header("Authorization", userData.authHeader())
                .contentType(ContentType.JSON)
                .body(updatePayload)
                .when().put(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.OK.value());

        // 使用新密碼構建認證頭
        String newAuthHeader = "Basic " + java.util.Base64.getEncoder()
                .encodeToString((email + ":" + newPassword).getBytes());

        given() // 使用新密碼應該能成功訪問
                .header("Authorization", newAuthHeader)
                .when().get(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.OK.value());

        given() // 使用舊密碼應該失敗
                .header("Authorization", userData.authHeader())
                .when().get(USER_ENDPOINT + "/" + userData.userId())
                .then().statusCode(HttpStatus.UNAUTHORIZED.value());
    }
}





