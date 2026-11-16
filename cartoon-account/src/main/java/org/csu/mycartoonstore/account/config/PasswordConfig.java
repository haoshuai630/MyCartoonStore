package org.csu.mycartoonstore.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码策略配置。
 *
 * <p>只引入 spring-security-crypto（不引整个 Spring Security），
 * 用 BCrypt 存散列：同一密码每次生成不同盐值，数据库泄露也无法直接还原。</p>
 */
@Configuration
public class PasswordConfig {

    /** 强度 10：约 100ms 一次，够用且不影响登录体验 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}