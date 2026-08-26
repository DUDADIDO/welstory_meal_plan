# 오늘의 한 끼

삼성 웰스토리 삼성전기 부산사업장(`REST000595`)의 중식 식단을 보여주는 React + Spring Boot 웹 애플리케이션입니다. 레거시 Mattermost 봇의 식단 조회 규칙을 웹 서비스로 옮겼습니다.

## 동작 방식

- Spring Boot 서버만 웰스토리 계정과 통신하며 자격증명은 브라우저에 전달되지 않습니다.
- 오늘 메뉴는 매일 06:00부터 확보하고, 사진은 09:00~18:00에 5분 간격으로 확인합니다.
- 오늘 요청은 캐시가 비어 있으면 즉시 조회하지만, 과거 미캐시 날짜는 웰스토리를 직접 호출하지 않고 관리자 범위 수집으로 채웁니다.
- 관리자 범위 수집은 한 번에 하나만 실행하며 실제 웰스토리 호출 사이에 30초 간격을 둡니다.
- 메뉴 이미지를 모두 내려받으면 그 날짜를 `READY`로 봉인합니다. 이후에는 서버 재시작 뒤에도 웰스토리를 다시 호출하지 않고 Docker 볼륨의 JSON과 이미지만 제공합니다.
- 완성된 API 응답은 12시간, 이미지는 30일 동안 브라우저/리버스 프록시에서 캐시할 수 있습니다.
- 주말과 휴일도 웰스토리 응답을 기준으로 처리하며, 확정된 식단 없음 결과도 캐시합니다.
- 식단 상태·별점·방문자 통계의 메타데이터는 PostgreSQL에 저장하고, 식단 이미지 파일만 Docker 캐시 볼륨에 저장합니다.

## Docker로 실행

```bash
cp .env.example .env
# .env의 WELSTORY_USERNAME / WELSTORY_PASSWORD 입력
docker compose up -d --build
```

`.env`에는 웰스토리 계정과 운영 콘솔 계정을 함께 설정합니다.

```dotenv
WELSTORY_USERNAME=웰스토리_아이디
WELSTORY_PASSWORD=웰스토리_비밀번호
ADMIN_USERNAME=admin
ADMIN_PASSWORD=충분히_긴_랜덤_비밀번호
POSTGRES_DB=welstory
POSTGRES_USER=welstory
POSTGRES_PASSWORD=충분히_긴_DB_비밀번호
```

기본 접속 주소는 `http://localhost:8080`입니다. PostgreSQL은 `welstory-postgres` 컨테이너와 `welstory-postgres` 볼륨으로 운영되며, Flyway가 최초 기동 시 스키마를 자동 생성합니다. 이미지는 `welstory-cache` Docker 볼륨에 보존됩니다. 홈 서버에서 HTTPS를 쓴다면 Caddy, Nginx Proxy Manager 같은 리버스 프록시를 이 컨테이너의 8080 포트 앞에 두면 됩니다.

상태 확인:

```bash
docker compose ps
curl http://localhost:8080/actuator/health
```

## 로컬 개발

요구 사항은 Java 21, Maven 3.9+, Node.js 22+입니다.

```bash
# API
mvn spring-boot:run

# 별도 터미널에서 React 개발 서버
cd frontend
npm install
npm run dev
```

Vite 개발 서버는 `/api` 요청을 `localhost:8080`으로 프록시합니다. 프로덕션 Docker 이미지는 React 정적 빌드를 Spring Boot jar에 포함하므로 컨테이너 하나만 실행됩니다.

## 주요 환경 변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `WELSTORY_USERNAME` | 없음 | 웰스토리 로그인 ID, 필수 |
| `WELSTORY_PASSWORD` | 없음 | 웰스토리 로그인 비밀번호, 필수 |
| `WELSTORY_RESTAURANT_CODE` | `REST000595` | 식당 코드 |
| `WELSTORY_MEAL_TYPE` | `2` | 중식 코드 |
| `WELSTORY_CACHE_DIR` | `./data/cache` | 영속 캐시 경로 |
| `POSTGRES_DB` | `welstory` | PostgreSQL 데이터베이스 이름 |
| `POSTGRES_USER` | `welstory` | PostgreSQL 사용자 |
| `POSTGRES_PASSWORD` | 없음 | PostgreSQL 비밀번호, 필수 변경 |
| `ADMIN_USERNAME` | 없음 | `/admin` 운영 콘솔 ID |
| `ADMIN_PASSWORD` | 없음 | `/admin` 운영 콘솔 비밀번호 |
| `APP_PORT` | `8080` | Docker 호스트 공개 포트 |

## API

- `GET /api/meals` — 한국 시간 기준 오늘의 식단
- `GET /api/meals?date=2026-08-26` — 지정 날짜 식단
- `GET /api/meals/{date}/images/{mealId}` — 서버에 캐시된 식단 이미지
- `GET /api/ratings?date=YYYY-MM-DD&clientId=...` — 날짜별 별점 조회
- `POST /api/ratings` — 식단 별점 저장
- `GET /api/admin/status` — 캐시·수집·별점 운영 상태(인증 필요)
- `POST /api/admin/refresh` — 오늘 식단 즉시 재확인(인증 필요)
- `POST /api/admin/cache-jobs` — 날짜 범위 순차 캐시 작업 시작(인증 필요)
- `DELETE /api/admin/cache-jobs/current` — 실행 중인 범위 작업 취소(인증 필요)
- `GET /api/admin/logs` — 최근 서버 로그 조회(인증 필요)
- `GET /actuator/health` — 컨테이너 상태

운영 콘솔은 `/admin`에서 접근합니다. HTTP Basic 인증을 사용하므로 홈 서버 외부에 공개할 때는 반드시 HTTPS 리버스 프록시 뒤에서 운영해야 합니다.
