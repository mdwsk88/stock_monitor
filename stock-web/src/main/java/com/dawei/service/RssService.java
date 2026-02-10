package com.dawei.service;

import com.rometools.rome.feed.synd.SyndEntry;

import java.util.List;

public interface RssService {
    public List<SyndEntry> fetchRssReed(String rssUrl) throws Exception;


    public void displayRss() throws Exception;
}
