# seckill
Java秒杀商城，基于 Spring Boot + Mybatis 实现的前后端分离系统。

# 单机部署
下载安装配置 MySQL，导入并运行 SQL 文件

下载安装配置 Redis

下载安装配置 RocketMQ

# 分布式
下载安装配置 MySQL，导入并运行 SQL 文件

下载安装配置 Redis

下载安装配置 RocketMQ

下载安装 OpenResty，并根据实际情况进行配置。

# 我的分布式部署
使用 4 台服务器，进行水平扩展

  1 台作为 Nginx 负载均衡，并作为静态资源服务器
  
  1 台作为 MySQL，Redis 数据库服务器
  
  2 台作为 应用服务器
