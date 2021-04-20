#!/usr/bin/env bash
# This script decrypts our test database
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io
# You will need the environment variables CIRCLE_CI_KEY_2 and CIRCLE_CI_IV_2

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

: "$CIRCLE_CI_KEY_2"
: "$CIRCLE_CI_IV_2"

openssl aes-256-cbc -d -in circle_ci_test_data.zip.enc -k "$CIRCLE_CI_KEY_2" -iv "$CIRCLE_CI_IV_2" -out secrets.tar
tar xvf secrets.tar
# Create the directory where the .pem file will be placed for continuous integration
# If you would like to change this path (might be a good idea since we no longer use
# TravisCI for CI) you need to change the path in dockstoreTest.yml which currently
# is in the encrypted bundle circle_ci_test_data.zip.enc
sudo mkdir -p /usr/local/ci
sudo cp dockstore-cli-integration-testing/src/test/resources/dstesting_pcks8.pem /usr/local/ci/dstesting_pcks8.pem
rm secrets.tar

