package com.dawei;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * @ClassName ServiceLogAspect
 * @Author 风间影月
 * @Version 1.0
 * @Description ServiceLogAspect
 **/
@Component
@Slf4j
//@Aspect
public class ServiceLogAspect {


    @Around("execution(* com.dawei.service.impl..*.*(..))")
    public Object recordTimeLog(ProceedingJoinPoint joinPoint) throws Throwable {

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

//        long beginTime = System.currentTimeMillis();

        Object proceed = joinPoint.proceed();
        String pointCut = joinPoint.getTarget().getClass().getName() + "." + joinPoint.getSignature().getName();

//        long endTime = System.currentTimeMillis();
        stopWatch.stop();

//        long takeTime = endTime - beginTime;
        long takeTime = stopWatch.getTotalTimeMillis();

        if (takeTime > 3000) {
            log.error("[{}], [{}], [{}], [{}]", pointCut, takeTime, "SLOW", joinPoint.getArgs());
        } else if (takeTime > 2000) {
            log.warn("[{}], [{}], [{}], [{}]", pointCut, takeTime, "NORMAL", joinPoint.getArgs());
        } else {
            log.info("[{}], [{}], [{}], [{}]", pointCut, takeTime, "OK", joinPoint.getArgs());
        }

        return proceed;
    }

}
