package com.chs.productservice.config;

import com.chs.productservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserService userService;

    /**
     * 定義安全過濾鏈 - 這是 Spring Security 6.x 的現代寫法
     * 使用 Lambda DSL 配置各種安全設定
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 禁用 CSRF - API 不需要 CSRF 保護
                .csrf(AbstractHttpConfigurer::disable)

                // 設定為無狀態 - 每個請求都要重新認證（符合 RESTful 設計）
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 配置請求權限
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/user").permitAll()    // POST 註冊不需認證
                        .requestMatchers("/health").permitAll()     // 健康檢查不需認證
                        .requestMatchers(HttpMethod.GET, "/v1/product").permitAll()     // 獲取所有產品不需認證
                        .requestMatchers(HttpMethod.GET, "/v1/product/*").permitAll()   // 獲取單個產品不需認證
                        .anyRequest().authenticated()               // 其他請求需要認證
                )

                // 啟用 HTTP Basic Authentication
                .httpBasic(httpBasic -> httpBasic.realmName("webapp"))

                .build();
    }

    /**
     * 用戶詳細服務 - 告訴 Spring Security 如何載入用戶資料
     * 現代寫法：直接返回 lambda 函數，不需要 DaoAuthenticationProvider
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return email -> {
            var user = userService.findByEmail(email);

            // 轉換為 Spring Security 的 UserDetails
            return org.springframework.security.core.userdetails.User.builder()
                    .username(user.getEmail())
                    .password(user.getPassword())  // 已經是雜湊過的密碼
                    .authorities("ROLE_USER")      // 設定權限
                    .build();
        };
    }

    /**
     * 認證管理器 - 用於手動認證（如果需要的話）
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
