FROM clojure:openjdk-17-lein-2.9.5-slim-buster
WORKDIR /usr/app
COPY . .
CMD [ "lein", "uberjar" ]

FROM openjdk:17-alpine
WORKDIR /usr/app
RUN apk update && \
  apk add openssh-client
COPY --from=0 /usr/app/example-config.edn /usr/app/config.edn
COPY --from=0 /usr/app/target/uberjar /usr/app
EXPOSE 8080
CMD [ "java", "-jar", "wgctrl-0.2.0-SNAPSHOT-standalone.jar" ]
