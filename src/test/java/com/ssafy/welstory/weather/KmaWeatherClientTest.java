package com.ssafy.welstory.weather;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class KmaWeatherClientTest {
    private static final String BASE = "https://kma.test";
    private static String url(String endpoint, String day, String time) {
        return BASE + "/" + endpoint + "?serviceKey=A%2BB%2FC%3D&pageNo=1&numOfRows=1000&dataType=JSON&base_date="
                + day + "&base_time=" + time + "&nx=94&ny=74";
    }
    private static String observations(String day, String time, String pty) {
        return "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":{\"item\":["
                + "{\"baseDate\":\""+day+"\",\"baseTime\":\""+time+"\",\"category\":\"T1H\",\"obsrValue\":\"23.4\"},"
                + "{\"baseDate\":\""+day+"\",\"baseTime\":\""+time+"\",\"category\":\"PTY\",\"obsrValue\":\""+pty+"\"},"
                + "{\"baseDate\":\""+day+"\",\"baseTime\":\""+time+"\",\"category\":\"RN1\",\"obsrValue\":\"0\"}]}}}}";
    }

    @Test
    void currentObservationsWinOverRainForecastAndEncodedKeyIsNotDoubleEncoded() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var now = Instant.parse("2026-10-13T03:59:00Z"); // 12:59 KST; hourly observation is still fresh.
        var service = new WeatherService(builder, "https://meteo.test", 35.09, 128.87,
                Clock.fixed(now, ZoneId.of("UTC")), "A%2BB%2FC%3D", BASE);
        server.expect(requestTo(url("getUltraSrtNcst", "20261013", "1200")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(observations("20261013", "1200", "0")));
        server.expect(requestTo(url("getUltraSrtFcst", "20261013", "1130")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("""
                        {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
                          {"fcstDate":"20261013","fcstTime":"1200","category":"SKY","fcstValue":"4"},
                          {"fcstDate":"20261013","fcstTime":"1200","category":"PTY","fcstValue":"1"},
                          {"fcstDate":"20261013","fcstTime":"1400","category":"SKY","fcstValue":"1"}
                        ]}}}}
                        """));
        var snapshot = service.current();
        assertThat(snapshot.provider()).isEqualTo("KMA");
        assertThat(snapshot.status()).isEqualTo("READY");
        assertThat(snapshot.condition()).isEqualTo("cloudy");
        assertThat(snapshot.temperature()).isEqualTo(23.4);
        assertThat(snapshot.observedAt()).isEqualTo(Instant.parse("2026-10-13T03:00:00Z"));
        service.current(); // Shared ten-minute cache, no extra calls.
        server.verify();
    }

    @Test
    void midnightRequestsPreviousDayAndMissingSkyDoesNotInventClearWeather() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var now = Instant.parse("2026-10-12T15:05:00Z"); // Oct 13 00:05 KST.
        var client = new KmaWeatherClient(builder, BASE, "A+B/C=", 35.09, 128.87);
        server.expect(requestTo(url("getUltraSrtNcst", "20261012", "2300")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(observations("20261012", "2300", "0")));
        server.expect(requestTo(url("getUltraSrtFcst", "20261012", "2330")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var result = client.fetch(now, now.plusSeconds(600));
        assertThat(result.temperature()).isEqualTo(23.4);
        assertThat(result.condition()).isNull();
        assertThat(result.observedAt()).isEqualTo(Instant.parse("2026-10-12T14:00:00Z"));
        server.verify();
    }

    @Test
    void kmaErrorCodeDoesNotProduceWeatherAndPrecipitationKindsStayDistinct() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new WeatherService(builder, "https://meteo.test", 35.09, 128.87,
                Clock.fixed(Instant.parse("2026-10-13T03:20:00Z"), ZoneId.of("UTC")), "A+B/C=", BASE);
        server.expect(requestTo(url("getUltraSrtNcst", "20261013", "1200")))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"response\":{\"header\":{\"resultCode\":\"30\"}}}"));
        assertThat(service.current().status()).isEqualTo("UNAVAILABLE");
        assertThat(service.current().provider()).isEqualTo("KMA");
        assertThat(KmaWeatherClient.classify(1, 1.0, 0.0).kind()).isEqualTo("rain");
        assertThat(KmaWeatherClient.classify(1, 1.0, 1.0).kind()).isEqualTo("storm");
        assertThat(KmaWeatherClient.classify(3, 4.0, 0.0).kind()).isEqualTo("snow");
        assertThat(KmaWeatherClient.classify(0, null, 0.0).kind()).isNull();
        assertThat(KmaWeatherClient.grid(35.09, 128.87)).isEqualTo(new KmaWeatherClient.Grid(94, 74));
        server.verify();
    }
}
