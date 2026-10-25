# Prerender (SEO-Snapshots für Bots) auf Basis von tvanro/prerender-alpine. Das Upstream-Image
# wird nicht mehr aktualisiert (letzter Stand 10/2025), deshalb hier Alpine-Pakete (Chromium,
# brotli-libs) und Node nachziehen. Anlass: CVE-2026-111497, Brotli-Decoder in Chromium und Node.
FROM node:22.23.4-alpine3.23 AS node

FROM tvanro/prerender-alpine:7.2.0
USER root
RUN apk upgrade --no-cache
# Das Upstream-Image bringt Node als Einzel-Binary unter /usr/local/bin mit, nicht per apk
COPY --from=node /usr/local/bin/node /usr/local/bin/node
USER node
