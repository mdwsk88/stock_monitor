package com.dawei.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * @ClassName BaiduTransEntity
 * @Author dawei
 * @Version 1.0
 * @Description BaiduTransEntity
 **/
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class BaiduTransEntity {

    private String from;
    private String to;
    private List<TransResult> trans_result;

    @Data
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransResult {
        private String src;
        private String dst;
    }

    /*{
        "from":"en",
        "to":"zh",
        "trans_result":[
                {
                    "src":"title en",
                    "dst":"title zh"
                }
        ]
    }*/

}
