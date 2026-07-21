# ClarityCam Backend (BE)

Thu muc nay la workspace backend doc lap. Mo rieng `D:\DemoReview\BE` bang VS Code.

## Chay local nhanh

Yeu cau Java 21, Maven va MySQL dang chay tai cong 3306.

```powershell
cd D:\DemoReview\BE
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

API chay tai `http://127.0.0.1:8080`.

Profile `local` dung tai khoan MySQL mac dinh `root` khong mat khau va bat du lieu demo.

Schema MySQL doc lap nam tai `D:\DemoReview\SQL\claritycam_platform.sql`. Co the
import file nay bang MySQL Workbench truoc khi khoi dong backend.

## Chay MySQL bang Docker

Sao chep `.env.example` thanh `.env`, sau do:

```powershell
docker compose --env-file .env up -d mysql
```

Nap cac bien `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` trong `.env` vao terminal/VS Code
truoc khi chay Spring Boot, hoac cau hinh chung trong `launch.json` cua may ban.

## Lenh kiem tra

```powershell
mvn test
mvn package
```
