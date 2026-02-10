package com.itzixi.utils;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Service
public class TransApi {

//    private static final String TRANS_API_HOST = "http://api.fanyi.baidu.com/api/trans/vip/translate";

    @Value("${baidu.translate.host}")
    private String host;
    @Value("${baidu.translate.appid}")
    private String appid;
    @Value("${baidu.translate.securityKey}")
    private String securityKey;

    // 添加了一个无参构造器，不然无法注入这个bean，否则需要额外的配置。
    public TransApi() {}

    @Resource
    private RestTemplate restTemplate;

    public TransApi(String appid, String securityKey) {
        this.appid = appid;
        this.securityKey = securityKey;
    }

    public String getTransResult(String query, String from, String to) {
        Map<String, String> params = buildParams(query, from, to);
        MultiValueMap<String, String> requestParams = new LinkedMultiValueMap<>();
        requestParams.setAll(params);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(host);
//        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TRANS_API_HOST);  // Springboot3 使用本行代码
        URI uri = builder.queryParams(requestParams).build().encode().toUri();  //  这里不能进行 encode 了，编码就错误了
//        System.out.println("uri: " + uri);
        return restTemplate.getForObject(uri, String.class);
    }

    private Map<String, String> buildParams(String query, String from, String to) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("q", query);
        params.put("from", from);
        params.put("to", to);

        params.put("appid", appid);

        // 随机数
        String salt = String.valueOf(System.currentTimeMillis());
        params.put("salt", salt);

        // 签名
        String src = appid + query + salt + securityKey; // 加密前的原文
        params.put("sign", MD5.md5(src));

        return params;
    }

}

