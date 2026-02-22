package vn.edu.phenikaa.timetablex.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.edu.phenikaa.timetablex.entity.UserAccount;
import vn.edu.phenikaa.timetablex.repository.UserAccountRepository;

/**
 * Khởi tạo tài khoản ADMIN (Phòng Đào tạo) mặc định nếu chưa có.
 * username: admin
 * password: Admin@123
 */
@Component
public class AdminInitializer {

    @Autowired
    private UserAccountRepository userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void initAdmin() {
        if (userRepo.existsByUsername("Admin")) {
            return;
        }
        UserAccount admin = UserAccount.builder()
                .username("Admin")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .role(UserAccount.Role.ADMIN)
                .active(true)
                .build();
        userRepo.save(admin);
    }
}

