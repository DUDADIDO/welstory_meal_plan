package com.ssafy.welstory.meal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.welstory.config.WelstoryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class WelstoryApiClientTest {

    @Test
    void parsesOctetStreamJsonAndUsesCourseTotalCalories() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WelstoryProperties properties = new WelstoryProperties(
                "https://welstory.test",
                "user",
                "password",
                "REST000595",
                "삼성전기 부산사업장",
                "2",
                null,
                Duration.ofMinutes(5),
                Duration.ofMinutes(30)
        );
        WelstoryApiClient client = new WelstoryApiClient(builder, properties, new ObjectMapper());

        server.expect(requestTo("https://welstory.test/login"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body("{}"));
        server.expect(requestTo("https://welstory.test/api/meal?menuDt=20260826&menuMealType=2&restaurantCode=REST000595"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body("""
                                {"data":{"mealList":[{
                                  "menuName":"제육볶음",
                                  "courseTxt":"한식사계",
                                  "subMenuTxt":"제육볶음, 밥, 국, 반찬",
                                  "photoUrl":"",
                                  "photoCd":"",
                                  "kcal":"420",
                                  "sumKcal":"1,250"
                                }]}}
                                """));

        var meals = client.fetchLunch(LocalDate.of(2026, 8, 26));

        assertThat(meals).hasSize(1);
        assertThat(meals.getFirst().calorie()).isEqualTo("1,250 kcal");
        server.verify();
    }
}
