# 构建第一个MCP工具

pom.xml
```
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>

    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
    </dependency>
    
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

</dependencies>
```

application.yml
```
server:
  port: 8090  # 服务器端口配置

spring:
  application:
    name: mcp-server
  profiles:
    active: dev
  ai:
    mcp:
      server:
        name: stock-monitor-mcp-server-sse      # MCP服务器名称
        version: 1.0.0                          # 服务器版本号
        sse-endpoint: /sse
        type: async                             #异步
```