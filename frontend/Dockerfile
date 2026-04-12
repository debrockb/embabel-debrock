FROM node:18-alpine

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy source code
COPY public public/
COPY src src/

# Build the app
RUN npm run build

# Serve with a lightweight HTTP server
FROM node:18-alpine
RUN npm install -g serve
WORKDIR /app
COPY --from=0 /app/build ./build

EXPOSE 3000

CMD ["serve", "-s", "build", "-l", "3000"]
