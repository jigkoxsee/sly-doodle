FROM mhart/alpine-node:latest

MAINTAINER Your Name <you@example.com>

# Create app directory
RUN mkdir -p /youtube-uploader
WORKDIR /youtube-uploader

# Install app dependencies
COPY package.json /youtube-uploader
RUN npm install pm2 -g
RUN npm install

# Bundle app source
COPY target/release/youtube-uploader.js /youtube-uploader/youtube-uploader.js
COPY public /youtube-uploader/public

ENV HOST 0.0.0.0

EXPOSE 3000
CMD [ "pm2-docker", "/youtube-uploader/youtube-uploader.js" ]
