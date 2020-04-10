FROM azul/zulu-openjdk-alpine:11.0.6

# figure out how you want to manage config files, etc, and call the service appropriately
CMD ["sh", "/deploy/ktor-demo/bin/ktor-demo", "config-dir-goes-here"]
EXPOSE 9080

RUN mkdir /deploy
ADD build/distributions/ktor-demo.tar /deploy
