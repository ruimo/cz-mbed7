#!/bin/sh -e
. watson-env.conf
bin/activator universal:packageZipTarball
docker build -t ${DOCKER_NS}/watsonproxy .
