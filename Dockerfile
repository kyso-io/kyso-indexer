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
RUN echo mkdir ./indexer/target &&\
 mv ./indexer-dependency ./indexer/target/dependency &&\
 ./mvnw install && ./mvnw package -Pnative

## Stage 2 : create the docker final image
FROM registry.access.redhat.com/ubi8/openjdk-17:1.11

COPY --from=compiler /code/api/target/quarkus-app/lib /work/lib
COPY --from=compiler /code/api/target/quarkus-app/*.jar /work
COPY --from=compiler /code/api/target/quarkus-app/app /work/app
COPY --from=compiler /code/api/target/quarkus-app/quarkus /work/quarkus

USER root
# set up permissions for user `1001`
RUN echo "setting permissions" \
  && chown -R 1001 /work \
  && chmod -R "g+rwX" /work \
  && chown -R 1001:root /work

WORKDIR /work
EXPOSE 8080
USER 1001
CMD ["java", "-jar", "quarkus-run.jar", "-Dquarkus.http.host=0.0.0.0"]