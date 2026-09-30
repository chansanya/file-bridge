# JDK 8 兼容版本说明

当前分支为 FileBridge 的 Java 8 兼容版本线，适用于仍运行在 **JDK 8 + Spring Boot 2.7.18** 的遗留业务系统。

## 与现代主线的关系

| 分支 | Java | Spring Boot | Servlet 命名空间 |
| --- | --- | --- | --- |
| `main` | 17+ | 4.x | `jakarta.*` |
| `codex/jdk8-compat` | 8+ | 2.7.18 | `javax.*` |

两条版本线保持相同的核心 REST 路径、数据库表结构和前端控制台协议，但 Java 二进制产物不能混用。

## Maven 依赖

```xml
<dependency>
  <groupId>io.github.chansan</groupId>
  <artifactId>file-bridge-spring-boot-starter</artifactId>
  <version>0.1.0-jdk8-SNAPSHOT</version>
</dependency>
```

## Java 8 适配内容

- 所有 `record` 领域对象已转换为不可变普通类；
- 同时保留 `fileId()` 风格访问器和标准 `getFileId()` JavaBean 访问器；
- `jakarta.servlet` / `jakarta.validation` 已退回 `javax.*`；
- Spring Boot 自动配置基线调整为 2.7.18，并同时注册 `spring.factories`；
- Java 9+ 集合工厂、流 API、I/O API、模式匹配和 switch 表达式已替换为 Java 8 实现；
- Starter、Example 和数据库迁移脚本保持原有业务协议。

## 构建要求

使用 JDK 8 执行：

```bash
java -version
./mvnw clean test
```

示例启动：

```bash
./mvnw install -DskipTests
java -jar example/target/example-0.1.0-jdk8-SNAPSHOT.jar
```

## 维护约束

- JDK 8 分支只接收兼容性修复、安全修复和可回移的业务缺陷修复；
- 新功能优先在 `main` 实现，再评估是否回移；
- 不允许在本分支重新引入 `record`、模式匹配 `instanceof`、箭头 switch、`Stream.toList()` 等 Java 8 不支持的语言或 API；
- Spring Boot 2.7 已进入维护末期，生产部署应由宿主团队负责依赖安全扫描和补丁策略。
