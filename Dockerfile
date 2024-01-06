FROM clojure:openjdk-17-lein-2.9.5-slim-buster
WORKDIR /usr/app
COPY . .
RUN lein uberjar

FROM openjdk:17-alpine
WORKDIR /usr/app
RUN apk update && \
  apk add openssh-client && \
  apk add bash
COPY --from=0 /usr/app/target/uberjar/*.jar /usr/app
COPY --from=0 /usr/app/scripts /usr/app/scripts
EXPOSE 8080
RUN ls
CMD [ "java", "-jar", "wgctrl-0.2.1-SNAPSHOT-standalone.jar" ]
