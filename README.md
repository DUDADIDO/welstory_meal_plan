# 오늘의 한 끼

삼성 웰스토리 삼성전기 부산사업장(`REST000595`)의 중식 식단을 보여주는 React + Spring Boot 웹 애플리케이션입니다. 레거시 Mattermost 봇의 식단 조회 규칙을 웹 서비스로 옮겼습니다.

## 동작 방식

- Spring Boot 서버만 웰스토리 계정과 통신하며 자격증명은 브라우저에 전달되지 않습니다.
- 오늘 메뉴는 매일 06:00부터 확보하고, 사진은 09:00~18:00에 5분 간격으로 확인합니다.
- 매일 한국 시간 자정에는 오늘부터 7일 뒤까지 메뉴 캐시를 강제로 다시 확인해 변경된 미래 식단을 반영합니다.
- 오늘 요청은 캐시가 비어 있으면 즉시 조회하지만, 과거 미캐시 날짜는 웰스토리를 직접 호출하지 않고 관리자 범위 수집으로 채웁니다.
- 관리자 범위 수집은 한 번에 하나만 실행하며 실제 웰스토리 호출 사이에 30초 간격을 둡니다.
- 메뉴 이미지를 모두 내려받으면 그 날짜를 `READY`로 봉인합니다. 이후에는 서버 재시작 뒤에도 웰스토리를 다시 호출하지 않고 Docker 볼륨의 JSON과 이미지만 제공합니다.
- 오늘·미래 식단 API 응답은 브라우저/리버스 프록시에 저장하지 않습니다. 완료된 과거 식단 응답은 12시간, 이미지는 내용 해시별로 30일 동안 캐시할 수 있습니다.
- 오늘 식단은 준비 완료 후에도 화면에서 1분마다 다시 조회하고, 다른 탭에서 돌아오거나 브라우저 뒤로 가기로 화면을 복원하면 즉시 재조회합니다. 사진이 바뀌면 이미지 URL의 내용 해시가 바뀌어 기존 이미지 캐시 대신 새 사진을 자동으로 받습니다. 서버의 미완료 사진 수집은 기존 5분 간격을 따릅니다.
- 주말과 휴일도 웰스토리 응답을 기준으로 처리하며, 확정된 식단 없음 결과도 캐시합니다.
- 식단 상태·별점·방문자 통계·익명 채팅은 PostgreSQL에 저장하고, 식단 이미지 파일만 Docker 캐시 볼륨에 저장합니다.

## 식단별 익명 채팅

각 식단 카드의 **별점 옆 채팅 아이콘**으로 해당 날짜·식단 전용 채팅방에 참여합니다. 음식 이름 200개에서 서로 다른 두 개를 골라 `김밥·푸딩` 같은 이름을 부여하며, 39,800개의 조합 중 사용자 간 중복 없이 배정합니다. 익명 이름은 모든 방에서 동일하고 서버 재시작 뒤에도 유지됩니다.

서버가 발급한 HttpOnly 쿠키와 별도의 브라우저 식별키(localStorage)를 함께 사용합니다. 256비트 난수 식별키의 SHA-256 해시를 서버에 연결해 쿠키만 삭제해도 기존 익명 이름·채팅 제한를 복원합니다. 기존 쿠키 사용자는 다음 접속 때 자동 연결합니다. 브라우저 저장소 사용이 차단되면 쿠키로 참여하며, 다른 브라우저·시크릿 창·사이트 데이터 전체 삭제는 새 사용자로 처리됩니다. 로그인 없는 사용자 구분은 동일인을 완벽하게 식별하지 못하며 MAC 주소를 수집하지 않습니다. 채팅에는 식별키와 쿠키 사용자 식별값을 공개하지 않습니다.

전송은 모든 방을 합산해 사용자당 **5초에 1개**, **한국 시간 기준 하루 50개**까지 가능합니다. 자정에 일일 횟수가 초기화되며, 방이나 탭을 바꾸어도 같은 쿠키의 제한은 공유합니다. DB 트랜잭션과 사용자 행 잠금으로 동시 전송을 검사하고, 제한에 걸리면 `429`와 `Retry-After`를 반환합니다. 메시지는 최대 500자이며 채팅방은 3초마다 갱신합니다. 최근 100개부터 보여주고 **이전 대화 보기**로 기록을 더 조회합니다.

채팅의 실제 PostgreSQL 통합 테스트는 운영 데이터와 분리된 임시 DB에서 실행합니다. 익명 이름 중복, 식단·사용자 구분, 정확한 5초 경계, 50개 제한, 자정 초기화, 동시 전송, 이전 기록 조회를 검증합니다.

```bash
docker compose -p welstory-chat-test -f docker-compose.chat-test.yml up --build --abort-on-container-exit --exit-code-from tests
docker compose -p welstory-chat-test -f docker-compose.chat-test.yml down --volumes
```

## 동일 메뉴의 과거 참고 사진

- 해당 날짜 사진이 없으면 정규화한 이름이 같은 더 이른 메뉴의 실제 사진을 **참고 사진**으로 표시합니다. 대괄호 태그, 공백·기호, 대소문자와 전각 문자 차이를 정리합니다. 정규화한 전체 이름이 정확히 같을 때만 연결하며, 이차돌 같은 메뉴 이름 안의 브랜드명과 음식 재료·괄호 안의 내용은 유지합니다. 유사도나 별칭으로 다른 메뉴를 연결하지 않습니다. 이름 전체가 대괄호인 `[라면 14종 중 택1]`은 메뉴 이름으로 보존합니다.
- 과거 사진의 날짜를 명시하며 현재 사진이 도착하면 자동으로 교체합니다. 일치하는 과거 사진이 없으면 준비 중 표시를 유지합니다.
- 푸터 기존 내용 오른쪽과 우측 하단 플로팅 영역의 코랄색 **Ko-fi** 버튼은 `https://ko-fi.com/starfbsdud` 후원 페이지를 새 탭으로 엽니다. 공식 컵 아이콘은 앱에 포함해 제공합니다.
- Docker 통합 테스트에는 참고 사진 선별·현재 사진 도착 후 교체와 쿠키 삭제 후 사용자 복원 검증도 포함됩니다.

## 플로팅 메뉴 투표

오늘 식단이 있는 날 우측 하단 투표 아이콘을 누르면 화면 중앙에 모달 투표창이 열립니다. 본문에는 투표 영역을 추가하지 않으며, 창이 열린 동안 30초마다 집계를 갱신합니다. 한 사람의 최종 선택은 한 표로 집계하고 다른 메뉴 선택으로 변경, 같은 메뉴 선택으로 취소할 수 있습니다. 닫기 버튼·Escape·배경 클릭으로 닫고 키보드 포커스와 스크롤은 모달 안에서 유지합니다. 투표 아이콘 옆 Ko-fi 후원 아이콘은 과거 날짜나 식단 없는 날에도 표시합니다.

서버가 사용자별 **5초에 1회·한국 시간 기준 하루 10회**로 선택·변경·취소를 제한합니다. 같은 선택의 중복 요청은 횟수를 추가 차감하지 않습니다. 채팅 횟수와 독립적으로 관리하고 쿠키 삭제 후 브라우저 식별키로 복원된 사용자에게도 같은 제한을 적용합니다. 오늘만 투표할 수 있으며 자정에 횟수가 초기화됩니다.

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
- `GET /api/chat/identity` — 익명 사용자 발급·조회 및 남은 전송 횟수
- `POST /api/chat/identity` — 브라우저 식별키 연결·사용자 복원 (본문: `browserKey`; 헤더: `X-Chat-Request: 1`)
- `GET /api/menu-history/references?date=YYYY-MM-DD` — 사진이 없는 메뉴의 과거 참고 사진
- `GET /api/chat/meal-votes?date=YYYY-MM-DD` — 투표 집계·내 선택·남은 횟수
- `POST /api/chat/meal-votes` — 오늘 메뉴 투표 (`date`, `mealId`; `mealId:null`로 취소; `X-Chat-Request: 1` 필요)
- `GET /api/chat/messages?date=YYYY-MM-DD&mealId=meal-01` — 식단별 채팅 조회 (`before=메시지ID`로 이전 기록 조회)
- `POST /api/chat/messages` — 채팅 전송 (본문: `date`, `mealId`, `content`; 헤더: `X-Chat-Request: 1`; 익명 쿠키 필요)
- `GET /api/admin/status` — 캐시·수집·별점 운영 상태(인증 필요)
- `POST /api/admin/refresh?date=YYYY-MM-DD&force=true` — 지정 날짜 식단 강제 재확인(기존 완료 캐시의 칼로리 보강 등에 사용, 인증 필요)
- `POST /api/admin/cache-jobs` — 날짜 범위 순차 캐시 작업 시작(본문에 `forceExisting:true`를 넣으면 완료 캐시도 재확인, 인증 필요)
- `DELETE /api/admin/cache-jobs/current` — 실행 중인 범위 작업 취소(인증 필요)
- `GET /api/admin/logs` — 최근 서버 로그 조회(인증 필요)
- `GET /actuator/health` — 컨테이너 상태

운영 콘솔은 `/admin`에서 접근합니다. HTTP Basic 인증을 사용하므로 홈 서버 외부에 공개할 때는 반드시 HTTPS 리버스 프록시 뒤에서 운영해야 합니다.
