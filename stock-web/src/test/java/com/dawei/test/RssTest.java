package com.dawei.test;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URL;
import java.util.List;

@SpringBootTest
@Disabled("需要外部网络连接和环境配置，暂时禁用")
public class RssTest {


    public static final String RSS_URL = "https://www.stocktitan.net/rss";


    public List<SyndEntry> fetchRssReed(String rssUrl) throws Exception{
        URL url = new URL(rssUrl);
        SyndFeedInput input =  new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(url));
        return feed.getEntries();
    }


    @Test
    public void testFetchRssReed() throws Exception{
        List<SyndEntry> list = this.fetchRssReed(RSS_URL);
        System.out.println(list);
    }

}
