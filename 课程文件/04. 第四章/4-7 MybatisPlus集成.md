# MybatisPlus集成

```
<!-- 数据库 MySql 的坐标 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.0.33</version>
</dependency>

<!-- MyBatis-Plus 的坐标 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.14</version>
    <exclusions>
        <exclusion>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis-spring</artifactId>
        </exclusion>
    </exclusions>
</dependency>

<dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis-spring</artifactId>
    <version>3.0.5</version>
</dependency>
```

```
spring:
  datasource: # 数据源的相关配置
    type: com.zaxxer.hikari.HikariDataSource      # 数据源的类型，可以更改为其他的数据源配置，比如druid
    driver-class-name: com.mysql.cj.jdbc.Driver      # mysql/MariaDB 的数据库驱动类名称
    url: jdbc:mysql://127.0.0.1:3306/us_stock_monitor_lee?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: root
    hikari:
      pool-name: DataSourceHikariCP           # 连接池的名字
      connection-timeout: 30000               # 等待连接池分配连接的最大时间（毫秒），超过这个时长还没有可用的连接，则会抛出SQLException
      minimum-idle: 5                         # 最小连接数
      maximum-pool-size: 20                   # 最大连接数
      auto-commit: true                       # 自动提交
      idle-timeout: 600000                    # 连接超时的最大时长（毫秒），超时则会被释放（retired）
      max-lifetime: 18000000                  # 连接池的最大生命时长（毫秒），超时则会被释放（retired）
      connection-test-query: SELECT 1
```

```
# MyBatisPlus 的配置
mybatis-plus:
  global-config:
    db-config:
      id-type:
      update-strategy: not_empty
  mapper-locations: classpath*:/mappers/*.xml
```

```
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.itzixi.mapper.USStockRssMapper">

    <!-- 通用查询映射结果 -->
    <resultMap id="BaseResultMap" type="com.itzixi.entity.USStockRss">
        <id column="id" property="id" />
        <result column="stock_code" property="stockCode" />
        <result column="title" property="title" />
        <result column="title_zh" property="titleZh" />
        <result column="link" property="link" />
        <result column="pub_date_gmt" property="pubDateGmt" />
        <result column="pub_date_bj" property="pubDateBj" />
        <result column="tags" property="tags" />
    </resultMap>

</mapper>
```