package com.dawei;


import com.dawei.service.RssService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @ClassName StockScheduler
 * @Author 风间影月
 * @Version 1.0
 * @Description StockScheduler
 **/
@Component
@EnableScheduling
public class StockScheduler {

    @Resource
    private RssService rssService;

//    常见的cron表达式示例：
//    */5 * * * * ? 每隔5秒执行一次
//    0 */1 * * * ? 每隔1分钟执行一次
//    0 0 5-15 * * ? 每天5-15点整点触发
//    0 0/3 * * * ? 每三分钟触发一次
//    0 0-5 14 * * ? 在每天下午2点到下午2:05期间的每1分钟触发
//    0 0/5 14 * * ? 在每天下午2点到下午2:55期间的每5分钟触发
//    0 0/5 14,18 * * ? 在每天下午2点到2:55期间和下午6点到6:55期间的每5分钟触发
//    0 0/30 9-17 * * ? 朝九晚五工作时间内每半小时
//    0 0 10,14,16 * * ? 每天上午10点，下午2点，4点

    // 定时任务，定时抓取股票数据信息
    //@Scheduled(cron = "0 */30 * * * ?")
    @Scheduled(initialDelay = 0, fixedDelay = 30 * 60 * 1000)
    public void getStockInfo() throws Exception {
        System.out.println("每隔一段时间运行..." + LocalDateTime.now());
        rssService.displayRss();
        System.out.println("定时任务执行结束=======================");
    }

}
