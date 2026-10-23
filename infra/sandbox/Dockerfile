FROM caddy:2.11.7-alpine
# El binario oficial exige NET_BIND_SERVICE mediante file capabilities.
# Aquí escucha en 8443: retirarlas permite cap_drop ALL y usuario sin privilegios.
RUN setcap -r /usr/bin/caddy
USER 1000:1000
