package com.ssafy.mapmates.service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.ssafy.mapmates.dao.NewsDao;
import com.ssafy.mapmates.dto.News;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 여행 뉴스 수집(F106): 뉴스 RSS(XML)를 파싱해 DB에 저장하고, 저장된 뉴스를 조회한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {
    private final NewsDao newsDao;
    private final RestClient restClient = RestClient.create();

    /** 최근 1시간 내 수집한 적이 없으면 새로 수집한 뒤 DB에서 조회한다. */
    public List<News> list(String keyword, int size) {
        String kw = keyword == null || keyword.isBlank() ? "국내 여행" : keyword.trim();
        if (newsDao.countRecent(kw) == 0) crawl(kw);
        return newsDao.findByKeyword(kw, Math.max(1, Math.min(size, 50)));
    }

    public int crawl(String keyword) {
        int saved = 0;
        try {
            String url = "https://news.google.com/rss/search?q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&hl=ko&gl=KR&ceid=KR:ko";
            byte[] xml = restClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // XXE 방지
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < Math.min(items.getLength(), 30); i++) {
                Element item = (Element) items.item(i);
                News news = new News();
                news.setKeyword(keyword);
                news.setTitle(text(item, "title"));
                news.setLink(text(item, "link"));
                news.setSource(text(item, "source"));
                news.setPubDate(text(item, "pubDate"));
                if (news.getTitle() == null || news.getLink() == null || !news.getLink().startsWith("http")) continue;
                saved += newsDao.insert(news);
            }
        } catch (Exception e) {
            log.warn("뉴스 수집 실패: {}", e.getMessage());
        }
        return saved;
    }

    private String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : list.item(0).getTextContent();
    }
}
