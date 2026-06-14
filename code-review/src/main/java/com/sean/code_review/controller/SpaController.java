package com.sean.code_review.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

    /**
     * Forward all React Router routes to index.html so the SPA handles routing.
     * Authentication is managed by the React app itself (via the /api/auth/me call).
     */
    @RequestMapping(value = {"/", "/dashboard", "/reviews/**", "/install"})
    public String forward() {
        return "forward:/index.html";
    }
}
