# Builder image
FROM openjdk:17-jdk-alpine AS builder

RUN apk add --update \
    curl \
    maven \
    && rm -rf /var/cache/apk/*

RUN apk add maven

WORKDIR /app

COPY ./ ./

RUN mvn clean install

# Production image
FROM openjdk:17-alpine AS service

# Install fswatch
RUN apk add --no-cache file git autoconf automake libtool gettext gettext-dev make g++ texinfo curl

ENV ROOT_HOME /root
ENV FSWATCH_BRANCH 1.16.0

WORKDIR ${ROOT_HOME}
RUN git clone https://github.com/emcrisostomo/fswatch.git

WORKDIR ${ROOT_HOME}/fswatch
RUN git checkout ${FSWATCH_BRANCH}
RUN ./autogen.sh && ./configure && make -j

RUN /root/fswatch/fswatch --help

WORKDIR /app

# Copy files required to run the application
# COPY --chown=java:java --from=builder /app/target/kyso-indexer-jar-with-dependencies.jar ./
COPY --from=builder /app/target/kyso-indexer-jar-with-dependencies.jar ./

# Container command
ENTRYPOINT ["java -jar kyso-indexer-jar-with-dependencies.jar"]
CMD ["."]
