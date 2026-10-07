package com.ssafy.welstory.chat;

import java.security.SecureRandom;

/** Two different food names, with a separator so each combination is unambiguous. */
final class FoodNames {
    static final String[] FOODS = ("""
            김밥 비빔밥 볶음밥 주먹밥 유부초밥 오므라이스 카레 덮밥 잡채 떡볶이
            불고기 갈비 삼겹살 보쌈 족발 닭갈비 찜닭 삼계탕 갈비탕 설렁탕
            곰탕 육개장 순대국 감자탕 해장국 된장찌개 김치찌개 순두부 부대찌개 청국장
            냉면 막국수 칼국수 수제비 잔치국수 비빔국수 콩국수 라면 우동 소바
            짜장면 짬뽕 탕수육 마파두부 깐풍기 볶음우동 쌀국수 팟타이 분짜 월남쌈
            파스타 라자냐 리조또 피자 햄버거 샌드위치 핫도그 토스트 스테이크 돈가스
            치킨 닭강정 새우튀김 오징어튀김 고로케 감자튀김 치즈볼 만두 군만두 딤섬
            김치전 파전 부추전 감자전 호박전 녹두전 계란말이 계란찜 두부조림 어묵탕
            미역국 콩나물국 북엇국 떡국 떡만둣국 떡갈비 순대 어묵 나물 샐러드
            연어초밥 참치초밥 새우초밥 장어구이 고등어구이 갈치조림 낙지볶음 오징어볶음 해물찜 꽃게탕
            전복죽 호박죽 팥죽 닭죽 누룽지 옥수수 고구마 감자 단호박 밤
            사과 배 복숭아 자두 살구 체리 딸기 블루베리 라즈베리 포도
            수박 참외 멜론 망고 파인애플 바나나 키위 오렌지 귤 레몬
            자몽 유자 석류 무화과 감 대추 코코넛 리치 용과 아보카도
            도넛 쿠키 마카롱 브라우니 와플 팬케이크 크레페 머핀 스콘 크루아상
            식빵 바게트 베이글 소금빵 단팥빵 슈크림빵 호떡 붕어빵 꽈배기 찹쌀도넛
            인절미 송편 약과 경단 백설기 찰떡 양갱 푸딩 젤리 초콜릿
            아이스크림 팥빙수 요거트 치즈 케이크 티라미수 치즈케이크 타르트 에그타르트 카스텔라
            아몬드 호두 땅콩 피스타치오 캐슈넛 마카다미아 해바라기씨 잣 건포도 프레첼
            슈니첼 타코 부리토 퀘사디아 나초 후무스 팔라펠 쿠스쿠스 뇨키 포카치아
            """).strip().split("\\s+");
    private static final SecureRandom RANDOM = new SecureRandom();

    private FoodNames() {}

    static String randomName() {
        int first = RANDOM.nextInt(FOODS.length);
        int second = RANDOM.nextInt(FOODS.length - 1);
        if (second >= first) second++;
        return FOODS[first] + "·" + FOODS[second];
    }
}
