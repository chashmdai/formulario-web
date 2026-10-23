FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates xmlsec1 curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -g 1000 pisee && useradd -u 1000 -g 1000 -M pisee
WORKDIR /opt/pisee
USER 1000:1000
# El binario y todo el material institucional se montan; nunca entran en una capa.
ENTRYPOINT ["/opt/pisee/NodoV2", "start"]
