# 오늘의 한 끼

삼성 웰스토리 삼성전기 부산사업장(`REST000595`)의 중식 식단을 보여주는 React + Spring Boot 웹 애플리케이션입니다. 레거시 Mattermost 봇의 식단 조회 규칙을 웹 서비스로 옮겼습니다.

## 동작 방식

- Spring Boot 서버만 웰스토리 계정과 통신하며 자격증명은 브라우저에 전달되지 않습니다.
- 평일 06:00~10:40에는 5분 간격으로 확인합니다. 정기 확인 이후에도 첫 방문 시 캐시가 비어 있으면 제한된 간격으로 한 번 더 확인합니다.
- 사용자 요청도 캐시가 비어 있을 때 조회를 시작하지만, 단일 실행 잠금과 최소 재시도 간격으로 동시 요청을 합칩니다.
- 메뉴 이미지를 모두 내려받으면 그 날짜를 `READY`로 봉인합니다. 이후에는 서버 재시작 뒤에도 웰스토리를 다시 호출하지 않고 Docker 볼륨의 JSON과 이미지만 제공합니다.
- 완성된 API 응답은 12시간, 이미지는 30일 동안 브라우저/리버스 프록시에서 캐시할 수 있습니다.
- 주말과 `WELSTORY_HOLIDAYS`에 지정한 날짜는 원격 조회를 생략합니다.

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
```

기본 접속 주소는 `http://localhost:8080`입니다. 캐시는 `welstory-cache` Docker 볼륨에 보존됩니다. 홈 서버에서 HTTPS를 쓴다면 Caddy, Nginx Proxy Manager 같은 리버스 프록시를 이 컨테이너의 8080 포트 앞에 두면 됩니다.

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
| `WELSTORY_HOLIDAYS` | 없음 | `YYYY-MM-DD` 쉼표 구분 휴무일 |
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
- `GET /actuator/health` — 컨테이너 상태

운영 콘솔은 `/admin`에서 접근합니다. HTTP Basic 인증을 사용하므로 홈 서버 외부에 공개할 때는 반드시 HTTPS 리버스 프록시 뒤에서 운영해야 합니다.
