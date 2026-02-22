package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import vn.edu.phenikaa.timetablex.repository.UserAccountRepository;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    @Autowired
    private UserAccountRepository userRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public List<Map<String, Object>> listUsers() {
        return userRepo.findAll().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole().name());
            m.put("active", u.getActive());
            if (u.getFaculty() != null) {
                m.put("facultyId", u.getFaculty().getId());
                m.put("facultyName", u.getFaculty().getName());
            }
            return m;
        }).toList();
    }

    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<?> toggleActive(@PathVariable Long id) {
        return userRepo.findById(id)
                .map(u -> {
                    u.setActive(!Boolean.TRUE.equals(u.getActive()));
                    userRepo.save(u);
                    return ResponseEntity.ok().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id) {
        return userRepo.findById(id)
                .map(u -> {
                    String newPass = generateRandomString(10);
                    // Hash mật khẩu mới và lưu vào database
                    u.setPasswordHash(passwordEncoder.encode(newPass));
                    userRepo.save(u);
                    
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("username", u.getUsername());
                    resp.put("newPassword", newPass);
                    return ResponseEntity.ok(resp);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private String generateRandomString(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}

