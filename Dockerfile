FROM ubuntu:18.04

RUN apt-get update && apt-get install -y software-properties-common

RUN apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 0x219BD9C9 \
    && apt-add-repository 'deb http://repos.azulsystems.com/ubuntu stable main'

RUN apt-get update && apt-get -y install \
    zulu-10

CMD ["sh", "/deploy/ktor-demo/bin/ktor-demo"]
EXPOSE 8080

RUN mkdir /deploy
ADD build/distributions/ktor-demo.tar /deploy
