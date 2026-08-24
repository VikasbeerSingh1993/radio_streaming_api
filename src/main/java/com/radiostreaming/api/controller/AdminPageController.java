package com.radiostreaming.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    @GetMapping("/admin")
    public String adminRedirect() {
        return "redirect:/admin/";
    }

    @GetMapping("/admin/")
    public String admin() {
        return "forward:/admin/index.html";
    }
}
