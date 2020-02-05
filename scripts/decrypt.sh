#!/usr/bin/env bash
# This script decrypts our test database 
# WARNING: Edit decrypt.template.mustache not decrypt.sh which is a generated file
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

if [[ "${TESTING_PROFILE}" == "confidential"* ]] || [[ "${TESTING_PROFILE}" == "regression"* ]] || [[ "${TESTING_PROFILE}" == "automated-review" ]] || [[ "${TESTING_PROFILE}" == "singularity-tests" ]]; then
    openssl aes-256-cbc -K $encrypted_04282e75bce6_key -iv $encrypted_04282e75bce6_iv -in secrets.tar.enc -out secrets.tar -d
    tar xvf secrets.tar
    mv dockstore-cli-integration-testing/src/test/resources/dstesting_pcks8.pem /home/travis/dstesting_pcks8.pem
fi
