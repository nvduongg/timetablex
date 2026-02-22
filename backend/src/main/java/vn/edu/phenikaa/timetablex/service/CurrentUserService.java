package vn.edu.phenikaa.timetablex.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.edu.phenikaa.timetablex.entity.UserAccount;
import vn.edu.phenikaa.timetablex.repository.UserAccountRepository;

import java.util.Optional;

/**
 * Lấy thông tin user đang đăng nhập từ SecurityContext.
 * Dùng để lọc dữ liệu theo khoa khi role = FACULTY.
 */
@Service
public class CurrentUserService {

    private final UserAccountRepository userRepo;

    public CurrentUserService(UserAccountRepository userRepo) {
        this.userRepo = userRepo;
    }

    public Optional<UserAccount> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !auth.isAuthenticated())
            return Optional.empty();
        String username = auth.getPrincipal().toString();
        return userRepo.findByUsername(username);
    }

    /** Trả về facultyId nếu user là FACULTY, null nếu ADMIN (P.ĐT) */
    public Long getCurrentFacultyId() {
        return getCurrentUser()
                .filter(u -> u.getRole() == UserAccount.Role.FACULTY)
                .map(u -> u.getFaculty() != null ? u.getFaculty().getId() : null)
                .orElse(null);
    }

    public boolean isFaculty() {
        return getCurrentUser()
                .map(u -> u.getRole() == UserAccount.Role.FACULTY)
                .orElse(false);
    }
}
