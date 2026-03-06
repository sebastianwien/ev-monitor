# Stage 1: Build Frontend
FROM node:20-alpine AS build-stage
WORKDIR /app
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
# Set API base URL to use Nginx proxy
ENV VITE_API_BASE_URL=/api
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine
# Copy frontend build to nginx html directory
COPY --from=build-stage /app/dist /usr/share/nginx/html
# Copy basic nginx config
COPY nginx/nginx.conf /etc/nginx/nginx.conf
# Copy site configuration template
COPY nginx/conf.d/app.conf.template /etc/nginx/templates/default.conf.template
# Copy .well-known files (Tesla Fleet API public key etc.)
COPY nginx/well-known /usr/share/nginx/well-known
