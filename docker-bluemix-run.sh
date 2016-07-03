#!/bin/sh
. watson-env.conf
cf ic run -p 80 -m 512 -e APP_SECRET=${APP_SECRET} registry.ng.bluemix.net/${DOCKER_NS}/watsonproxy
