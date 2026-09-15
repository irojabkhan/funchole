#!/bin/sh

set -eu

. /opt/funchole/openbao-common.sh

wait_for_openbao
load_bao_token

exec java -jar /app/app.jar
