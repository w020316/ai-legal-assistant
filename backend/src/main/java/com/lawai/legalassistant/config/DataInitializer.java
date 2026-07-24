package com.lawai.legalassistant.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawai.legalassistant.modules.auth.entity.SysUser;
import com.lawai.legalassistant.modules.auth.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 数据初始化器
 * <p>
 * 首次启动时自动创建管理员账户，确保系统可用。
 * 管理员凭据通过环境变量配置，默认 admin/admin123。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${lawai.init.admin.username:admin}")
    private String adminUsername;

    @Value("${lawai.init.admin.password:${ADMIN_PASSWORD}}")
    private String adminPassword;

    public DataInitializer(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, adminUsername));
        if (count != null && count > 0) {
            log.info("管理员账户 [{}] 已存在，跳过初始化", adminUsername);
            return;
        }

        SysUser admin = new SysUser();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        admin.setStatus(1);
        admin.setCreatedAt(Instant.now());
        admin.setUpdatedAt(Instant.now());
        userMapper.insert(admin);
        log.info("===== 管理员账户 [{}] 初始化完成 =====", adminUsername);
        log.info("密码已通过环境变量 ADMIN_PASSWORD 设置，请妥善保管");
    }
}
