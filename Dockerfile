FROM azul/zulu-openjdk-alpine:11.0.6

# figure out how you want to manage config files, etc, and call the service appropriately
CMD ["sh", "/deploy/word-games/bin/word-games", "run-server"]
EXPOSE 9080

RUN mkdir /deploy
ADD build/distributions/word-games.tar /deploy
