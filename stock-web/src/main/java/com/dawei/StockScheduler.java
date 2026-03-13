package com.dawei;


import com.dawei.service.RssService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @ClassName StockScheduler
 * @Author dawei
 * @Version 1.0
 * @Description 股票定时任务调度器 - 按交易时段动态调整频率
 * 
 * 【优化说明】
 * 1. A股高频期：工作日 9:00-22:00（交易时段+晚间公告高发期），每5分钟一次
 * 2. A股低频期：工作日夜间，每小时一次兜底
 * 3. 美股高频期：工作日 21:30-次日6:00（美股交易时段，夏令时），每30分钟一次
 * 4. 美股低频期：工作日非交易时段，每小时一次兜底
 * 5. 周末巡逻：周六、周日每两小时补扫一次，避免遗漏重磅公告和政策
 **/
@Component
@EnableScheduling
@Slf4j
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

    // ============== 美股定时任务 ==============

    /**
     * 美股高频抓取：工作日晚间 21:30-06:00（覆盖美股交易时段）
     * 夏令时美股交易时间：21:30-次日04:00（北京时间）
     * 冬令时美股交易时间：22:30-次日05:00（北京时间）
     * 每30分钟抓取一次，确保及时获取异动新闻
     */
    @Scheduled(cron = "0 0/30 21-23,0-6 * * MON-FRI")
    public void getUSStockInfoHighFreq() throws Exception {
        log.info("【美股高频】开始抓取美股数据... {}", LocalDateTime.now());
        rssService.displayRss();
        log.info("【美股高频】定时任务执行结束=======================");
    }

    /**
     * 美股低频兜底：工作日白天时段，每小时检查一次
     * 覆盖时间：06:00-21:30
     */
    @Scheduled(cron = "0 0 6-21 * * MON-FRI")
    public void getUSStockInfoLowFreq() throws Exception {
        log.info("【美股低频】开始抓取美股数据... {}", LocalDateTime.now());
        rssService.displayRss();
        log.info("【美股低频】定时任务执行结束=======================");
    }

    // ============== A股定时任务 ==============

    /**
     * A股高频抓取：工作日 9:00-22:00
     * 覆盖交易时段（9:30-11:30, 13:00-15:00）和晚间公告高发期
     * 每5分钟抓取一次，确保及时获取公告信息
     */
    @Scheduled(cron = "0 0/5 9-22 * * MON-FRI")
    public void getAStockInfoHighFreq() throws Exception {
        log.info("【A股高频】开始抓取A股公告... {}", LocalDateTime.now());
        rssService.fetchAndSaveAStockNotices();
        log.info("【A股高频】定时任务执行结束=======================");
    }

    /**
     * A股低频兜底：工作日夜间及凌晨时段，每小时检查一次
     * 覆盖时间：23:00-次日8:00
     */
    @Scheduled(cron = "0 0 23,0,1,2,3,4,5,6,7,8 * * MON-FRI")
    public void getAStockInfoLowFreq() throws Exception {
        log.info("【A股低频】开始抓取A股公告... {}", LocalDateTime.now());
        rssService.fetchAndSaveAStockNotices();
        log.info("【A股低频】定时任务执行结束=======================");
    }

    /**
     * 周末低频巡逻：周六、周日每两小时查一次，防止错过周末重磅突发消息
     */
    @Scheduled(cron = "0 0 0/2 * * SAT,SUN")
    public void weekendLowFreqMonitor() throws Exception {
        log.info("【周末巡逻】开始扫描周末积压消息... {}", LocalDateTime.now());
        rssService.displayRss();
        rssService.fetchAndSaveAStockNotices();
        log.info("【周末巡逻】定时任务执行结束=======================");
    }

}
