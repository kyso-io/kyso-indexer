## Stage 1 : build with maven builder image with native capabilities
FROM quay.io/quarkus/ubi-quarkus-native-image:22.0-java17 AS build

COPY --chown=quarkus:quarkus api/mvnw /code/mvnw
COPY --chown=quarkus:quarkus api/.mvn /code/.mvn
COPY --chown=quarkus:quarkus api/pom.xml /code/
#USER quarkus
# With user quarkus is not able to perform mvn clean install... so I used root instead :P
USER root
WORKDIR /code
COPY . /code
RUN ./mvnw clean install

WORKDIR /code/api
RUN ./mvnw package -Pnative

## Stage 2 : create the docker final image
FROM quay.io/quarkus/quarkus-micro-image:1.0
WORKDIR /work/
COPY --from=build /code/api/target/*-runner /work/kyso-indexer

# set up permissions for user `1001`
RUN chmod 775 /work /work/kyso-indexer \
  && chown -R 1001 /work \
  && chmod -R "g+rwX" /work \
  && chown -R 1001:root /work

EXPOSE 8080
USER 1001

CMD ["./kyso-indexer", "-Dquarkus.http.host=0.0.0.0"]