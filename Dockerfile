## Stage 0 : build with maven builder image with native capabilities
FROM quay.io/quarkus/ubi-quarkus-native-image:22.0-java17 AS builder
USER root
WORKDIR /code
COPY ./pom.xml ./
COPY ./api/mvnw ./
COPY ./api/.mvn/ ./.mvn/
COPY ./api/pom.xml ./api/
COPY ./indexer/pom.xml ./indexer/
RUN ./mvnw dependency:copy-dependencies -pl indexer\
 -DoutputDirectory=/code/indexer-dependency

# Stage 1 : build with image with the dependencies already downloaded
FROM builder as compiler
COPY ./pom.xml ./
COPY ./api/mvnw ./
COPY ./api/ ./api/
COPY ./indexer/ ./indexer/
RUN mkdir ./indexer/target &&\
 mv ./indexer-dependency ./indexer/target/dependency &&\
 ./mvnw install && ./mvnw package -Pnative

## Stage 2 : create the docker final image
FROM quay.io/quarkus/quarkus-micro-image:1.0
COPY --from=compiler /code/api/target/*-runner /work/kyso-indexer
# set up permissions for user `1001`
RUN chmod 775 /work /work/kyso-indexer \
  && chown -R 1001 /work \
  && chmod -R "g+rwX" /work \
  && chown -R 1001:root /work
WORKDIR /work
EXPOSE 8080
USER 1001
CMD ["./kyso-indexer", "-Dquarkus.http.host=0.0.0.0"]
