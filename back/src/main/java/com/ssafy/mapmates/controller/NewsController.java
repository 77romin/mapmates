package com.ssafy.mapmates.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.mapmates.dto.News;
import com.ssafy.mapmates.service.NewsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {
    private final NewsService newsService;

    @GetMapping
    public List<News> list(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "20") int size) {
        return newsService.list(keyword, size);
    }
}
