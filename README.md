# ClarityCam Backend

Repository nay chi chua Spring Boot backend va co the deploy truc tiep len Render.

## Chay local nhanh

Yeu cau Java 21, Maven va MySQL dang chay tai cong 3306.

```powershell
cd D:\DemoReview\RescueBest
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

API chay tai `http://127.0.0.1:8080`.

Profile `local` dung tai khoan MySQL mac dinh `root` khong mat khau va bat du lieu demo.

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

## Deploy len Render

Repository co `Dockerfile` va `render.yaml` o thu muc goc. Tao Blueprint moi tren
Render tu repository nay, sau do khai bao cac bien bi mat trong dashboard:

- `DB_URL`: JDBC URL cua MySQL.
- `DB_USERNAME`, `DB_PASSWORD`: tai khoan MySQL.
- `CORS_ORIGINS`: domain frontend duoc phep goi API.
- `CLARITYCAM_ADMIN_PASSWORD`: mat khau admin manh.
- `IDENTITY_ENCRYPTION_KEY`: khoa bi mat dai, ngau nhien.
