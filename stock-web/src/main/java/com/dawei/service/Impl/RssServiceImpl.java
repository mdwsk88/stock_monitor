package com.dawei.service.Impl;

import com.dawei.service.RssService;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.List;
@Service
public class RssServiceImpl implements RssService {

    public static final String RSS_URL = "https://www.stocktitan.net/rss";

    @Override
    public void displayRss() throws Exception {
        List<SyndEntry> syndEntryList = this.fetchRssReed(RSS_URL);
        System.out.println(syndEntryList);
    }

    @Override
    public List<SyndEntry> fetchRssReed(String rssUrl) throws Exception {
        URL url = new URL(rssUrl);
        SyndFeedInput input =  new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(url));
        return feed.getEntries();
    }
}
