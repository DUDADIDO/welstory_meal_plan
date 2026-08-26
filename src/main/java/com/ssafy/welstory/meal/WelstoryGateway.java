package com.ssafy.welstory.meal;

import java.time.LocalDate;
import java.util.List;

public interface WelstoryGateway {
    List<MealModels.UpstreamMeal> fetchLunch(LocalDate date);
    MealModels.DownloadedImage downloadImage(String url);
}
