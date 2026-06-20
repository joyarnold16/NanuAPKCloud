FROM node:24-alpine

WORKDIR /app
COPY package.json ./
COPY src ./src
RUN addgroup -S nanu && adduser -S nanu -G nanu && mkdir -p /data && chown -R nanu:nanu /app /data
USER nanu
EXPOSE 8080
CMD ["node", "src/index.js"]
