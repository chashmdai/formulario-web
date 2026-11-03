FROM caddy:2.11.7-alpine@sha256:eb4d35b7452d1ab6171fbdbe8d9275b2301dad6dad92115d91e19fb9b24d510f
# El binario oficial exige NET_BIND_SERVICE mediante file capabilities.
# Aquí escucha en 8443: retirarlas permite cap_drop ALL y usuario sin privilegios.
RUN setcap -r /usr/bin/caddy && apk add --no-cache curl
USER 1000:1000
