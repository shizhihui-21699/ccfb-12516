package com.secaware.phishdrill.security;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.ReactiveAuthenticationProvider;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * 管理后台的内存账号校验（用户名/密码来自 application.yml 或环境变量）。
 * 演练面向员工的模拟邮箱、追踪链接全部匿名可访问，不走这里。
 *
 * 使用 Micronaut Security 4.x 的 ReactiveAuthenticationProvider 新接口，
 * 同时服务于 HTTP Basic（管理后台浏览器/测试）与 Bearer 登录。
 */
@Singleton
public class AdminAuthProvider implements ReactiveAuthenticationProvider<HttpRequest<?>, String, String> {

    private final String adminUser;
    private final String adminPassword;

    public AdminAuthProvider(@Value("${admin.username}") String adminUser,
                             @Value("${admin.password}") String adminPassword) {
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    @Override
    public Publisher<AuthenticationResponse> authenticate(HttpRequest<?> request,
                                                          AuthenticationRequest<String, String> authRequest) {
        return Flux.create(emitter -> {
            String identity = authRequest.getIdentity();
            String secret = authRequest.getSecret();
            if (adminUser.equals(identity) && adminPassword.equals(secret)) {
                emitter.next(AuthenticationResponse.success(adminUser, List.of("ROLE_ADMIN")));
                emitter.complete();
            } else {
                emitter.error(AuthenticationResponse.exception(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH));
            }
        }, FluxSink.OverflowStrategy.ERROR);
    }
}
