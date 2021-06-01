#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd $DIR && docker pull acryldata/datahub-upgrade:latest && docker run --env-file env/docker.env acryldata/datahub-upgrade:latest "$@"
