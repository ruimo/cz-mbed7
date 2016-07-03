#!/bin/sh
# In case cf push fails with the following error:
# unauthorized: authentication required
# $ cf ic login
. watson-env.conf
docker tag ${DOCKER_NS}/watsonproxy registry.ng.bluemix.net/${DOCKER_NS}/watsonproxy
docker push registry.ng.bluemix.net/${DOCKER_NS}/watsonproxy
