FROM java:8-jdk
MAINTAINER Shisei Hanai<ruimo.uno@gmail.com>

ADD target/universal /opt/watsonproxy
RUN cd /opt/watsonproxy && \
  cmd=$(basename *.tgz .tgz) && \
  tar xf ${cmd}.tgz && \
  echo /opt/watsonproxy/$cmd/bin/watsonproxy -Dhttp.port=80 -Dplay.crypto.secret=\${APP_SECRET} > launch.sh && \
  chmod +x launch.sh

EXPOSE 80

ENTRYPOINT ["/bin/bash", "-c", "/opt/watsonproxy/launch.sh"]