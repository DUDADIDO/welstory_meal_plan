# 1. 가벼운 Node.js 환경 선택
FROM node:22-slim

# 2. 컨테이너 내 작업 디렉토리 설정
WORKDIR /app

# 3. 라이브러리 설치를 위해 설정 파일 복사
COPY package*.json ./

# 4. 의존성 라이브러리 설치
RUN npm install --production

# 5. 나머지 소스 코드 복사
COPY . .

# 6. 실행 명령 (봇 실행 후 컨테이너 종료됨)
CMD ["node", "app.js"]