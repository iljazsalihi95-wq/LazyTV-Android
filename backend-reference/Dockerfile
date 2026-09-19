FROM node:20-bookworm-slim
WORKDIR /app
COPY package*.json ./
RUN npm install --omit=dev
COPY . .
RUN mkdir -p /app/data
ENV NODE_ENV=production DATABASE_PATH=/app/data/lazytv.sqlite PORT=8080
EXPOSE 8080
CMD ["sh","-c","node src/migrate.js && node src/server.js"]
