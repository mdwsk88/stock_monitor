package com.itzixi;


import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * Spring Boot 4 + MyBatis/MyBatis-Plus 的兼容性变化导致的需要手动装配mybatis
 * 后续mybatis兼容后应该可以解决本问题
 */
@Configuration
@MapperScan("com.itzixi.mapper")
public class MyBatisPlusConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);

        // 如果你有 XML
         factoryBean.setMapperLocations(
              new PathMatchingResourcePatternResolver()
              .getResources("classpath*:/mappers/*.xml")
         );

        return factoryBean.getObject();
    }
}