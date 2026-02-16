package com.dawei.service;

import com.rometools.rome.feed.synd.SyndEntry;

import java.util.List;

public interface RssService {

    // ============== 美股相关方法 ==============
    public List<SyndEntry> fetchRssReed(String rssUrl) throws Exception;

    public void displayRss() throws Exception;

    // ============== A股相关方法 ==============
    public void fetchAndSaveAStockNotices() throws Exception;

}
