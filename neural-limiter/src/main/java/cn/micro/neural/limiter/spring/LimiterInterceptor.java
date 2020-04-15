package cn.micro.neural.limiter.spring;

import cn.micro.neural.limiter.*;
import cn.neural.common.extension.ExtensionLoader;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * LimitInterceptor
 *
 * @author lry
 */
@Slf4j
@Aspect
@Configuration
@EnableConfigurationProperties(LimiterConfig.class)
public class LimiterInterceptor implements InitializingBean {

    private ILimiter limiter;

    @Autowired
    private LimiterConfig limiterConfig;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.limiter = ExtensionLoader.getLoader(ILimiter.class).getExtension(limiterConfig.getExtension());
    }

    @Around("execution(public * *(..)) && @annotation(cn.micro.neural.limiter.Limiter)")
    public Object interceptor(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Limiter limiterAnnotation = method.getAnnotation(Limiter.class);

        // 根据限流类型获取不同的key ,如果不传我们会以方法名作为key
        String key;
        if (LimitType.IP == limiterAnnotation.type()) {
            key = getIpAddress();
        } else if (LimitType.CUSTOMER == limiterAnnotation.type()) {
            key = limiterAnnotation.key();
            if (key.length() == 0) {
                key = method.getName().toUpperCase();
            }
        } else {
            return pjp.proceed();
        }

        boolean result;
        try {
            result = limiter.callRate(key, limiterAnnotation.rateMax(), limiterAnnotation.ratePeriod());
        } catch (Throwable e) {
            log.error("The call rate exception", e);
            return pjp.proceed();
        }

        if (result) {
            return pjp.proceed();
        } else {
            throw new LimiterException("You have been dragged into the blacklist");
        }
    }

    /**
     * 获取id地址
     *
     * @return Ip address
     */
    private String getIpAddress() {
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(
                RequestContextHolder.getRequestAttributes())).getRequest();
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        return ip;
    }

}