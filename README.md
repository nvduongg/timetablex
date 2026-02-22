# TimetableX

Hệ thống quản lý xếp thời khóa biểu – UCTP Phenikaa University.

## Công nghệ

| Thành phần | Stack |
|------------|--------|
| **Backend** | Java 17, Spring Boot 4, PostgreSQL, JWT |
| **Frontend** | React 19, Vite, Ant Design 6, Recharts |
| **Database** | PostgreSQL 16 |
| **Chạy môi trường** | Docker Compose |

## Yêu cầu

- Docker & Docker Compose
- Git

## Chạy dự án

```bash
# Clone repo
git clone <repository-url>
cd timetablex

# Khởi động toàn bộ (DB + Backend + Frontend)
docker compose up -d

# Xem log
docker compose logs -f
```

### Truy cập

| Dịch vụ | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8080 |
| PostgreSQL | localhost:5432 |

### Tài khoản mặc định

- Admin: `admin` / `admin123` (hoặc theo cấu hình trong `AdminInitializer`)

## Cấu trúc thư mục

```
timetablex/
├── backend/          # Spring Boot API
├── frontend/         # React + Vite
├── compose.yaml      # Docker Compose
└── README.md
```

## Lệnh Docker thường dùng

```bash
# Khởi động
docker compose up -d

# Dừng
docker compose down

# Build/compile trong container
docker exec timetablex-frontend npm run build
docker exec timetablex-backend ./mvnw compile

# Xem log
docker compose logs backend -f
docker compose logs frontend -f
```

## License

Nội bộ – Phenikaa University.
