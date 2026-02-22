package vn.edu.phenikaa.timetablex.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import vn.edu.phenikaa.timetablex.config.JwtUtil;
import vn.edu.phenikaa.timetablex.entity.UserAccount;
import vn.edu.phenikaa.timetablex.repository.FacultyRepository;
import vn.edu.phenikaa.timetablex.repository.UserAccountRepository;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserAccountRepository userRepo;
    @Autowired
    private FacultyRepository facultyRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        Optional<UserAccount> userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getActive())) {
            return ResponseEntity.status(401).body(Map.of("message", "Sai tên đăng nhập hoặc tài khoản bị khóa"));
        }
        UserAccount user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("message", "Sai mật khẩu"));
        }
        String token = jwtUtil.generateToken(user);

        // Map.of không chấp nhận giá trị null nên dùng HashMap
        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("token", token);
        resp.put("username", user.getUsername());
        resp.put("role", user.getRole().name());
        if (user.getFaculty() != null) {
            resp.put("facultyId", user.getFaculty().getId());
            resp.put("facultyName", user.getFaculty().getName());
        }
        return ResponseEntity.ok(resp);
    }

    /** ADMIN tạo tài khoản Khoa/Viện với username/password ngẫu nhiên. */
    @PostMapping("/create-faculty-account")
    public ResponseEntity<?> createFacultyAccount(@RequestBody Map<String, Object> body) {
        Long facultyId = body.get("facultyId") != null ? ((Number) body.get("facultyId")).longValue() : null;
        if (facultyId == null || facultyRepo.findById(facultyId).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Khoa không hợp lệ"));
        }
        String randomUsername = generateRandomString(8);
        while (userRepo.existsByUsername(randomUsername)) {
            randomUsername = generateRandomString(8);
        }
        String rawPassword = generateRandomString(10);

        UserAccount user = UserAccount.builder()
                .username(randomUsername)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(UserAccount.Role.FACULTY)
                .faculty(facultyRepo.findById(facultyId).orElseThrow())
                .active(true)
                .build();
        userRepo.save(user);

        return ResponseEntity.ok(Map.of(
                "username", randomUsername,
                "password", rawPassword
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestAttribute(name = "username", required = false) String username) {
        if (username == null) return ResponseEntity.status(401).build();
        Optional<UserAccount> userOpt = userRepo.findByUsername(username);
        if (userOpt.isEmpty()) return ResponseEntity.status(401).build();
        UserAccount user = userOpt.get();
        return ResponseEntity.ok(Map.of(
                "username", user.getUsername(),
                "role", user.getRole().name(),
                "facultyId", user.getFaculty() != null ? user.getFaculty().getId() : null
        ));
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

