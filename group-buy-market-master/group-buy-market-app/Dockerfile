# 基础镜像
FROM eclipse-temurin:8-jre-jammy

# 作者
LABEL maintainer="qsyy"

# 配置
ENV PARAMS=""

# 时区
ENV TZ=PRC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 添加应用
ADD target/group-buy-market-app.jar /group-buy-market-app.jar

ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /group-buy-market-app.jar $PARAMS"]
