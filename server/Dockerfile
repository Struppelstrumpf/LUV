FROM node:20-alpine

WORKDIR /app

RUN apk add --no-cache fontconfig font-dejavu

COPY package.json ./
RUN npm install --omit=dev

COPY src ./src

RUN mkdir -p /data

ENV PORT=8080
ENV DATA_DIR=/data
EXPOSE 8080

CMD ["npm", "start"]
