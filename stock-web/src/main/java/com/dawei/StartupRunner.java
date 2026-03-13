package com.dawei;

import com.dawei.service.RssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * @ClassName StartupRunner
 * @Author dawei
 * @Version 1.0
 * @Description 项目启动时执行的任务
 * 
 * 【功能说明】
 * 1. 美股低频任务：启动时立即执行一次，然后由定时任务按 Cron 调度
 * 2. A股低频任务：启动时立即执行一次，然后由定时任务按 Cron 调度
 * 
 * 这样可以确保项目启动后不需要等待到下一个整点就能获取最新数据
 **/
@Component
@Slf4j
public class StartupRunner implements CommandLineRunner {

    @Resource
    private RssService rssService;

    @Override
    public void run(String... args) {
        log.info("========== 项目启动，执行初始化数据抓取任务 ==========");

        // 1. 执行美股低频任务（获取兜底数据）
        try {
            log.info("【启动任务】开始执行美股低频数据抓取...");
            rssService.displayRss();
            log.info("【启动任务】美股低频数据抓取完成 ✓");
        } catch (Exception e) {
            log.error("【启动任务】美股低频数据抓取失败: {}", e.getMessage(), e);
        }

        // 2. 执行A股低频任务（获取兜底数据）
        try {
            log.info("【启动任务】开始执行A股低频数据抓取...");
            rssService.fetchAndSaveAStockNotices();
            log.info("【启动任务】A股低频数据抓取完成 ✓");
        } catch (Exception e) {
            log.error("【启动任务】A股低频数据抓取失败: {}", e.getMessage(), e);
        }

        log.info("========== 项目启动初始化任务执行完毕 ==========");
    }
}
