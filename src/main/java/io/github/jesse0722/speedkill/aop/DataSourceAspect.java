package io.github.jesse0722.speedkill.aop;

import io.github.jesse0722.speedkill.mysql.DynamicDataSourceHolder;
import org.aopalliance.intercept.Joinpoint;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author Lijiajun
 * @date 2020/11/29 22:10
 */
@Aspect
//@Component
public class DataSourceAspect {

    /***
     * 定义切点：所有修改数据源的操作
     */
    @Pointcut("execution(* io.github.jesse0722.speedkill.service..*.*(..))")
    public void exec() {}

    @Before("exec()")
    public void setKey(JoinPoint joinpoint){
        String methodName = joinpoint.getSignature().getName();
        if (methodName.startsWith("update") || methodName.startsWith("insert") || methodName.startsWith("delete") ||
                methodName.startsWith("set")) {
            DynamicDataSourceHolder.markMaster();
            System.out.println("连接到主库");
        } else if (methodName.startsWith("get") || methodName.startsWith("select") || methodName.startsWith("find")) {
            DynamicDataSourceHolder.markSlave();
            System.out.println("连接到从库");
        } else {
            DynamicDataSourceHolder.markMaster();
        }

    }
}
