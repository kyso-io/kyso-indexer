ARG ALPINE_VERSION=fixme
# Builder image
FROM registry.kyso.io/docker/alpine:${ALPINE_VERSION} as builder
RUN apk update && apk add --no-cache openjdk17 maven\
 && rm -rf /var/cache/apk/*
WORKDIR /app
COPY ./ ./
RUN mvn clean package

# Production image
FROM registry.kyso.io/docker/alpine:${ALPINE_VERSION} as service
# Install fswatch
RUN apk update && apk add --no-cache openjdk17-jre inotify-tools\
 && rm -rf /var/cache/apk/*
WORKDIR /app
# Copy inotify script
COPY inotify-run-indexer .
# Copy files required to run the application
COPY --from=builder /app/target/kyso-indexer-jar-with-dependencies.jar ./
# Declare /data as a volume
VOLUME /data
# Container command
CMD ["/bin/sh", "/app/inotify-run-indexer", "/data"]
