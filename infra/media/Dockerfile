FROM ubuntu:24.04 AS build
RUN apt-get update && apt-get install -y --no-install-recommends gcc libc6-dev linux-libc-dev && rm -rf /var/lib/apt/lists/*
COPY sandbox.c /sandbox.c
RUN gcc -O2 -Wall -Wextra -Werror /sandbox.c -o /sandbox
FROM ubuntu:24.04
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends python3 python3-defusedxml ffmpeg qpdf poppler-utils \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /sandbox /opt/sandbox
COPY inspector.py /opt/inspector.py
USER 1000:1000
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
ENTRYPOINT ["python3","/opt/inspector.py"]
