ARG ALPINE_VERSION=latest
# Builder image
FROM registry.kyso.io/docker/alpine:${ALPINE_VERSION} as builder
RUN apk update && apk add --no-cache openjdk17 curl maven\
 && rm -rf /var/cache/apk/*
WORKDIR /app
COPY ./ ./
RUN mvn clean install

# Production image
FROM registry.kyso.io/docker/alpine:${ALPINE_VERSION} as service
# Install fswatch
RUN apk update \
 && apk add --no-cache openjdk17 file git autoconf automake libtool gettext\
 gettext-dev make g++ texinfo curl\
 && git clone --depth 1 https://github.com/emcrisostomo/fswatch.git -b 1.16.0\
 && cd fswatch && ./autogen.sh && ./configure && make install && cd ..\
 && apk del file git autoconf automake libtool gettext gettext-dev make g++\
 texinfo curl\
 && rm -rf fswatch /var/cache/apk/*
WORKDIR /app
# Copy files required to run the application
COPY --from=builder /app/target/kyso-indexer-jar-with-dependencies.jar ./
# Declare /data as a volume
VOLUME /data

# Container command
CMD ["java", "-jar", "kyso-indexer-jar-with-dependencies.jar", "/data"]
