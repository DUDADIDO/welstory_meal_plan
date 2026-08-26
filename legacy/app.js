import { WelstoryClient, WelstoryRestaurant } from "welstory-api-wrapper";
import { isHoliday } from "korean-holidays";
import axios from "axios";
import dotenv from "dotenv";

dotenv.config();

async function runSsafyUltimateBot() {
  const client = new WelstoryClient();
  const restaurantCode = "REST000595"; // 삼성전기 부산 (SSAFY 부산)

  try {
    // 1. 날짜 설정 및 휴일 체크
    const now = new Date();
    const kstOffset = 9 * 60 * 60 * 1000;
    const kstDate = new Date(now.getTime() + kstOffset);
    const formattedDate = kstDate.toISOString().slice(0, 10);
    const day = kstDate.getUTCDay();

    // [수동 공휴일 설정]
    const manualHolidays = [
      "2026-05-01", // 근로자의 날
      "2026-06-03", // 제9회 전국동시지방선거
    ];

    if (
      day === 0 ||
      day === 6 ||
      isHoliday(kstDate) ||
      manualHolidays.includes(formattedDate)
    ) {
      console.log(
        `🚩 휴일(${formattedDate})이므로 봇을 실행하지 않고 종료합니다.`,
      );
      return;
    }

    // 2. 웰스토리 데이터 수집
    await client.login({
      username: process.env.WELSTORY_USERNAME,
      password: process.env.WELSTORY_PASSWORD,
    });

    const todayStr = formattedDate.replace(/-/g, "");
    const restaurant = new WelstoryRestaurant(
      client,
      restaurantCode,
      "삼성전기 부산",
    );
    const rawMeals = await restaurant.listMeal(parseInt(todayStr), "2"); // 점심(2)

    if (!rawMeals || rawMeals.length === 0) {
      console.log("❌ 오늘 식단 데이터가 없습니다.");
      return;
    }

    // 3. 메뉴 필터링 (최대 6개)
    const excludeKeywords = ["코인", "품목", "음료", "베이커리"];
    const meals = rawMeals
      .filter((m) => {
        const target = (m.name || "") + (m.menuCourseName || "");
        return !excludeKeywords.some((key) => target.includes(key));
      })
      .slice(0, 6);

    // 4. 3열 요약 테이블 생성 함수
    function createTable(mealGroup, startIndex) {
      const corners = `| ${mealGroup.map((m, i) => `**${startIndex + i}️⃣ ${m.menuCourseName}**`).join(" | ")} |`;
      const divider = `| ${mealGroup.map(() => ":---:").join(" | ")} |`;
      const images = `| ${mealGroup.map((m) => `![식단](${(m.photoUrl || "") + (m.photoCd || "")})`).join(" | ")} |`;
      const names = `| ${mealGroup.map((m) => `**${m.name}**`).join(" | ")} |`;
      return `${corners}\n${divider}\n${images}\n${names}`;
    }

    let tableMarkdown = "";
    for (let i = 0; i < meals.length; i += 3) {
      tableMarkdown += createTable(meals.slice(i, i + 3), i + 1) + "\n\n";
    }

    // 5. Mattermost API 설정
    const api = axios.create({
      baseURL: process.env.MATTERMOST_URL,
      headers: {
        Authorization: `Bearer ${process.env.MATTERMOST_TOKEN}`,
        "X-CSRF-Token": process.env.MATTERMOST_CSRF,
        "Content-Type": "application/json",
      },
    });

    // 6. 메인 메시지 전송 (고급 카드 레이아웃)
    const postResponse = await api.post("/api/v4/posts", {
      channel_id: process.env.MATTERMOST_CHANNEL_ID,
      message: `## 🍱 오늘의 SSAFY 점심 메뉴 (${formattedDate})`,
      props: {
        attachments: [
          {
            color: "#007AFF",
            title: "🍙 전체 식단",
            text: tableMarkdown,
            footer: "💡 메뉴 번호와 동일한 이모지를 클릭하여 투표하세요!",
            footer_icon: "https://i.imgur.com/1cfKxvM.png",
          },
        ],
      },
    });

    const postId = postResponse.data.id;

    // 7. 자동 투표 이모지 반응 생성
    const emojis = ["one", "two", "three", "four", "five", "six"];
    for (let i = 0; i < meals.length; i++) {
      await api.post("/api/v4/reactions", {
        user_id: process.env.MATTERMOST_USER_ID,
        post_id: postId,
        emoji_name: emojis[i],
      });
      await new Promise((res) => setTimeout(res, 300));
    }

    // 8. 상세 반찬 구성을 댓글(Thread)로 자동 생성
    const subMenuDetails = meals
      .map((m, i) => `${i + 1}️⃣ **${m.name}**\n📋 구성: ${m.subMenuTxt}`)
      .join("\n\n");
    await api.post("/api/v4/posts", {
      channel_id: process.env.MATTERMOST_CHANNEL_ID,
      root_id: postId,
      message: `### 📝 메뉴별 상세 반찬 구성\n---\n${subMenuDetails}`,
    });

    console.log("🚀 SSAFY 프리미엄 식단봇 전송이 완료되었습니다!");
  } catch (error) {
    const errorMsg = error.response
      ? JSON.stringify(error.response.data)
      : error.message;
    console.error("📂 실행 중 오류 발생:", errorMsg);
  }
}

runSsafyUltimateBot();
