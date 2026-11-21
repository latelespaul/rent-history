# syntax=docker/dockerfile:1
# =============================================================================
# Rentrix web image: static Vite build served by unprivileged nginx (D-04).
#   docker build -t rentrix-web rentrix-web
#   docker run -p 8081:8080 -e API_BASE_URL=https://api.example.com/api/v1 rentrix-web
# The API base URL is set at container start (API_BASE_URL -> /config.js), so one
# image works in every environment. Unset, the app calls /api/v1 on its own origin.
# =============================================================================

# --- Build stage ------------------------------------------------------------
FROM node:22.12.0-alpine AS builder

WORKDIR /build

COPY package.json package-lock.json .npmrc ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
ARG VITE_APP_NAME=Rentrix
ENV VITE_APP_NAME=${VITE_APP_NAME}
RUN npm run build

# --- Runtime stage ----------------------------------------------------------
FROM nginxinc/nginx-unprivileged:1.27.3-alpine

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --chmod=755 docker/40-rentrix-config.sh /docker-entrypoint.d/40-rentrix-config.sh
COPY --from=builder /build/dist /usr/share/nginx/html

ENV API_BASE_URL=""
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
