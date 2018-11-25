package com.tikal.kafkafuze.controllers;

import java.util.HashMap;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController  {

    private static final String template = "Hello, %s!";

    @RequestMapping("/")
    public HashMap getHomeRoutes() {

        HashMap<String, String> homeRoutes = new HashMap<String, String>();
        
        homeRoutes.put("message", "welcome");
        homeRoutes.put("graphiql", "/graphiql");
        homeRoutes.put("graphql", "/graphql");

        return homeRoutes;
    }
}
