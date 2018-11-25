package com.tikal.kafkafuze.controllers;

import java.util.HashMap;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/api/metrics")
public class HomeController {

    @RequestMapping("")
    public HashMap<String, String> getHomeRoutes() {

        HashMap<String, String> homeRoutes = new HashMap<String, String>();
        
        homeRoutes.put("message", "welcome");
        homeRoutes.put("graphiql", "/graphiql");
        homeRoutes.put("graphql", "/graphql");

        return homeRoutes;
    }
}
