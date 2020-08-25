FROM eu.gcr.io/malt-build/jre:master

ARG APP_NAME=mongo-postgresql-streamer
ENV APP_NAME=${APP_NAME}
ARG APP_ARCHIVE=jar
ENV APP_ARCHIVE=${APP_ARCHIVE}
# Override APP_VERSION when we build image within a container context instead (gitlab instead of bamboo), use automated commit SHA1, set version at jar/war build time with mvn version:set accordingly also.
ARG APP_VERSION=0.0.1-SNAPSHOT
ENV APP_VERSION=${APP_VERSION}

ENV SPRING_MANAGEMENT_PORT=8444
#ENV SPRING_PORT=8443
ENV SPRING_CLOUD_QUARTZ_ENABLED=true
ENV SPRING_CLOUD_VAULT_ENABLED=false
ENV SPRING_APPLICATION_NAME=${APP_NAME}
ENV SPRING_CLOUD_VAULT_GENERIC_APPLICATION_NAME=${APP_NAME}
#ENV SERVER_PORT=${SPRING_PORT}
ENV MANAGEMENT_SERVER_PORT=${SPRING_MANAGEMENT_PORT}

# You may want to set Xmx here when not running on a kubernetes platform with resources limit set (local dev), otherwise, the JVM limits will be unconstrained.
ENV JAVA_TOOL_OPTIONS=""

RUN addgroup -S malt -g 998 && adduser -S -G malt -u 999 -s /bin/bash -h /opt/malt-app malt
RUN mkdir -p /var/log/malt-app /opt/malt-app /etc/malt-app /vault

# Replace APP_VERSION with a wildcard if we build within a reproductible container context (gitlab vs bamboo)
COPY target/${APP_NAME}-${APP_VERSION}.${APP_ARCHIVE}  /opt/malt-app/${APP_NAME}.${APP_ARCHIVE}

RUN chown -R root:malt /opt/malt-app && \
    chmod 770 /opt/malt-app && \
    chmod 640 /opt/malt-app/* && \
    chown -R root:malt /var/log/malt-app && \
    chmod 770 /var/log/malt-app && \
    chown -R root:malt /etc/malt-app && \
    chmod 750 /etc/malt-app

#EXPOSE ${SPRING_PORT}
EXPOSE ${SPRING_MANAGEMENT_PORT}

# Unprivileged container runtime
USER malt
WORKDIR /opt/malt-app

CMD ["java", "-XX:+UnlockExperimentalVMOptions", \
  "-XX:+UseCGroupMemoryLimitForHeap", \
  "-Xss512k", \
  "-Dfile.encoding=UTF8", \
  "-jar", "mongo-postgresql-streamer.jar", \
  "--spring.config.additional-location=/etc/malt-app/"]