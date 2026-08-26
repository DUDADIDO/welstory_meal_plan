package com.ssafy.welstory.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {
    @GetMapping("/admin")
    public String admin() {
        return "forward:/index.html";
    }
}
