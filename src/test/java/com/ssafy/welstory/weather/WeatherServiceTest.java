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

class WeatherServiceTest {
    private static final String URL = "https://weather.test/v1/forecast?latitude=35.09&longitude=128.87&current=temperature_2m,weather_code,is_day,precipitation,cloud_cover&daily=sunrise,sunset&forecast_days=1&timezone=Asia/Seoul&timeformat=unixtime";

    @Test
    void currentWeatherIsSharedAndRefreshesAfterTenMinutesWithFailureBackoff() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var clock = new TestClock(Instant.parse("2026-10-13T03:00:00Z"));
        var weather = new WeatherService(builder, "https://weather.test", 35.09, 128.87, clock);
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"current":{"time":1791860400,"temperature_2m":22.5,"weather_code":63,"is_day":1,"precipitation":1.2,"cloud_cover":99},"daily":{"sunrise":[1791840900],"sunset":[1791881400]}}
                        """));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(weather.current().status()).isEqualTo("READY");
        assertThat(weather.current().condition()).isEqualTo("rain");
        assertThat(weather.current().temperature()).isEqualTo(22.5);
        assertThat(weather.current().sunrise()).isEqualTo(Instant.ofEpochSecond(1791840900));
        assertThat(weather.current().sunset()).isEqualTo(Instant.ofEpochSecond(1791881400));
        clock.now = clock.now.plusSeconds(599);
        assertThat(weather.current().status()).isEqualTo("READY");
        clock.now = clock.now.plusSeconds(1);
        assertThat(weather.current().status()).isEqualTo("STALE");
        assertThat(weather.current().temperature()).isEqualTo(22.5);
        assertThat(weather.current().sunrise()).isEqualTo(Instant.ofEpochSecond(1791840900));
        assertThat(weather.current().sunset()).isEqualTo(Instant.ofEpochSecond(1791881400));
        weather.refresh(); // No repeated provider request during the failure window.
        assertThat(weather.current().status()).isEqualTo("STALE");
        server.verify();
    }

    @Test
    void missingCurrentDataNeverBecomesFakeClearWeather() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var weather = new WeatherService(builder, "https://weather.test", 35.09, 128.87, Clock.systemUTC());
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("{\"current\":{}}"));
        assertThat(weather.current().status()).isEqualTo("UNAVAILABLE");
        assertThat(weather.current().condition()).isNull();
        server.verify();
    }

    @Test
    void staleProviderTimeAndUnsupportedCodesAreNotPresentedAsCurrentWeather() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var clock = new TestClock(Instant.parse("2026-10-13T05:00:00Z"));
        var weather = new WeatherService(builder, "https://weather.test", 35.09, 128.87, clock);
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                .body("{\"current\":{\"time\":1791860400,\"temperature_2m\":22.5,\"weather_code\":0,\"is_day\":1}}"));
        assertThat(weather.current().status()).isEqualTo("STALE");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> WeatherService.condition(999))).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void wmoCodesDistinguishRainSnowStormAndFog() {
        assertThat(WeatherService.condition(0).kind()).isEqualTo("clear");
        assertThat(WeatherService.condition(3).kind()).isEqualTo("cloudy");
        assertThat(WeatherService.condition(48).kind()).isEqualTo("fog");
        for (int code : new int[]{51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82}) assertThat(WeatherService.condition(code).kind()).isEqualTo("rain");
        for (int code : new int[]{71, 73, 75, 77, 85, 86}) assertThat(WeatherService.condition(code).kind()).isEqualTo("snow");
        for (int code : new int[]{95, 96, 99}) assertThat(WeatherService.condition(code).kind()).isEqualTo("storm");
    }

    private static class TestClock extends Clock {
        private Instant now;
        TestClock(Instant now) { this.now = now; }
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
